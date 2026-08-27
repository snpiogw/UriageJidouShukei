package com.example.salesaggregation.web;

import com.example.salesaggregation.application.*;
import com.example.salesaggregation.application.AdminViewModels.SettingsView;
import com.example.salesaggregation.domain.TaxMode;
import com.example.salesaggregation.domain.TriggerType;
import jakarta.validation.Valid;
import org.quartz.SchedulerException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.UUID;

@Controller
public class AdminController {
    private final AdminQueryService queryService;
    private final AdminSettingsService settingsService;
    private final AggregationLaunchService launchService;

    public AdminController(AdminQueryService queryService, AdminSettingsService settingsService,
                           AggregationLaunchService launchService) {
        this.queryService = queryService;
        this.settingsService = settingsService;
        this.launchService = launchService;
    }

    @GetMapping("/login")
    String login() { return "login"; }

    @GetMapping({"/", "/admin"})
    String dashboard(Model model) {
        AdminQueryService.DashboardData data = queryService.dashboard();
        SettingsView settings = data.settings();
        if (!model.containsAttribute("settingsForm")) model.addAttribute("settingsForm", form(settings));
        model.addAttribute("history", data.history());
        model.addAttribute("latestAttempt", data.latestAttempt());
        model.addAttribute("latestSuccess", data.latestSuccess());
        model.addAttribute("nextExecution", data.nextExecution());
        model.addAttribute("taxModes", TaxMode.values());
        return "admin";
    }

    @PostMapping("/admin/settings")
    String updateSettings(@Valid @ModelAttribute SettingsForm settingsForm, BindingResult binding,
                          Authentication authentication, RedirectAttributes redirects) {
        if (binding.hasErrors()) {
            redirects.addFlashAttribute("org.springframework.validation.BindingResult.settingsForm", binding);
            redirects.addFlashAttribute("settingsForm", settingsForm);
            return "redirect:/admin";
        }
        try {
            settingsService.update(settingsForm.getTaxMode(), settingsForm.getTaxRate(),
                    settingsForm.isAutoEnabled(), settingsForm.getExecutionTime(), settingsForm.getVersion(),
                    authentication.getName());
            redirects.addFlashAttribute("message", "設定を保存しました");
        } catch (ObjectOptimisticLockingFailureException ex) {
            redirects.addFlashAttribute("error", "設定が別の操作で更新されました。内容を確認して再度保存してください");
        } catch (SchedulerException ex) {
            redirects.addFlashAttribute("error", "設定は保存されましたが、自動実行予定を更新できませんでした");
        }
        return "redirect:/admin";
    }

    @PostMapping("/admin/executions")
    String execute(RedirectAttributes redirects) {
        UUID id = launchService.launch(TriggerType.MANUAL);
        redirects.addFlashAttribute("message", "集計処理を受け付けました");
        return "redirect:/admin/executions/" + id;
    }

    @GetMapping("/admin/executions/{id}")
    String execution(@PathVariable UUID id, Model model) {
        AdminQueryService.ExecutionDetail detail = queryService.execution(id);
        model.addAttribute("execution", detail.execution());
        model.addAttribute("errors", detail.errors());
        model.addAttribute("refresh", !detail.execution().status().isTerminal());
        return "execution";
    }

    @PostMapping("/admin/executions/{id}/restart")
    String restart(@PathVariable UUID id, RedirectAttributes redirects) {
        try {
            launchService.restart(id);
            redirects.addFlashAttribute("message", "失敗地点から集計処理を再開しました");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirects.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/executions/" + id;
    }

    private SettingsForm form(SettingsView settings) {
        SettingsForm form = new SettingsForm();
        form.setTaxMode(settings.taxMode());
        BigDecimal taxRate = settings.taxRate().stripTrailingZeros();
        form.setTaxRate(taxRate.scale() < 0 ? taxRate.setScale(0) : taxRate);
        form.setAutoEnabled(settings.autoEnabled());
        form.setExecutionTime(settings.executionTime());
        form.setVersion(settings.version());
        return form;
    }
}

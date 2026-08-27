package com.example.salesaggregation.web;

import com.example.salesaggregation.application.*;
import com.example.salesaggregation.domain.ColumnMapping;
import com.example.salesaggregation.domain.TaxMode;
import com.example.salesaggregation.domain.TriggerType;
import com.example.salesaggregation.infrastructure.persistence.AggregationProfileEntity;
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
    private final AdminQueryService queries;
    private final AggregationProfileService profiles;
    private final AdminProfileService profileAdmin;
    private final AggregationLaunchService launches;

    public AdminController(AdminQueryService queries, AggregationProfileService profiles,
                           AdminProfileService profileAdmin, AggregationLaunchService launches) {
        this.queries = queries;
        this.profiles = profiles;
        this.profileAdmin = profileAdmin;
        this.launches = launches;
    }

    @GetMapping("/login")
    String login() { return "login"; }

    @GetMapping({"/", "/admin", "/admin/profiles"})
    String profiles(Model model) {
        model.addAttribute("profiles", queries.profiles());
        return "admin";
    }

    @GetMapping("/admin/profiles/new")
    String newProfile(Model model) {
        if (!model.containsAttribute("profileForm")) model.addAttribute("profileForm", new AggregationProfileForm());
        return profileForm(model, null);
    }

    @PostMapping("/admin/profiles")
    String create(@Valid @ModelAttribute("profileForm") AggregationProfileForm form, BindingResult binding,
                  Authentication authentication, Model model, RedirectAttributes redirects) {
        if (binding.hasErrors()) return profileForm(model, null);
        try {
            AggregationProfileEntity saved = profileAdmin.create(form.toCommand(), authentication.getName());
            redirects.addFlashAttribute("message", "集計設定を登録しました");
            return "redirect:/admin/profiles/" + saved.getId() + "/edit";
        } catch (IllegalArgumentException ex) {
            binding.reject("profile.invalid", ex.getMessage());
            return profileForm(model, null);
        } catch (SchedulerException ex) {
            redirects.addFlashAttribute("error", "設定は保存されましたが、自動実行予定を更新できませんでした");
            return "redirect:/admin/profiles";
        }
    }

    @GetMapping("/admin/profiles/{id}/edit")
    String edit(@PathVariable long id, Model model) {
        if (!model.containsAttribute("profileForm")) model.addAttribute("profileForm", form(profiles.get(id)));
        return profileForm(model, id);
    }

    @PostMapping("/admin/profiles/{id}")
    String update(@PathVariable long id,
                  @Valid @ModelAttribute("profileForm") AggregationProfileForm form, BindingResult binding,
                  Authentication authentication, Model model, RedirectAttributes redirects) {
        if (binding.hasErrors()) return profileForm(model, id);
        try {
            profileAdmin.update(id, form.getVersion(), form.toCommand(), authentication.getName());
            redirects.addFlashAttribute("message", "集計設定を保存しました");
            return "redirect:/admin/profiles/" + id + "/edit";
        } catch (ObjectOptimisticLockingFailureException ex) {
            binding.reject("profile.conflict", "設定が別の操作で更新されました。再読み込みして確認してください");
            return profileForm(model, id);
        } catch (IllegalArgumentException ex) {
            binding.reject("profile.invalid", ex.getMessage());
            return profileForm(model, id);
        } catch (SchedulerException ex) {
            redirects.addFlashAttribute("error", "設定は保存されましたが、自動実行予定を更新できませんでした");
            return "redirect:/admin/profiles/" + id + "/edit";
        }
    }

    @PostMapping("/admin/profiles/{id}/executions")
    String execute(@PathVariable long id, RedirectAttributes redirects) {
        try {
            UUID executionId = launches.launch(id, TriggerType.MANUAL);
            redirects.addFlashAttribute("message", "集計処理を受け付けました");
            return "redirect:/admin/executions/" + executionId;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirects.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/profiles";
        }
    }

    @PostMapping("/admin/executions")
    String executeLegacy(RedirectAttributes redirects) {
        return execute(AggregationProfileService.LEGACY_PROFILE_ID, redirects);
    }

    @GetMapping("/admin/profiles/{id}/executions")
    String history(@PathVariable long id, Model model) {
        AdminQueryService.ProfileHistory history = queries.profileHistory(id);
        model.addAttribute("profile", history.profile());
        model.addAttribute("history", history.history());
        return "profile-history";
    }

    @GetMapping("/admin/executions/{id}")
    String execution(@PathVariable UUID id, Model model) {
        AdminQueryService.ExecutionDetail detail = queries.execution(id);
        model.addAttribute("execution", detail.execution());
        model.addAttribute("errors", detail.errors());
        model.addAttribute("refresh", !detail.execution().status().isTerminal());
        return "execution";
    }

    @PostMapping("/admin/executions/{id}/restart")
    String restart(@PathVariable UUID id, RedirectAttributes redirects) {
        try {
            launches.restart(id);
            redirects.addFlashAttribute("message", "失敗地点から集計処理を再開しました");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirects.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/executions/" + id;
    }

    private String profileForm(Model model, Long id) {
        model.addAttribute("profileId", id);
        model.addAttribute("taxModes", TaxMode.values());
        model.addAttribute("pageTitle", id == null ? "集計設定の新規登録" : "集計設定の編集");
        return "profile-form";
    }

    private AggregationProfileForm form(AggregationProfileEntity profile) {
        AggregationProfileForm form = new AggregationProfileForm();
        form.setProfileName(profile.getProfileName());
        form.setSpreadsheetId(profile.getSpreadsheetId());
        form.setSourceSheetName(profile.getSourceSheetName());
        form.setResultSheetName(profile.getResultSheetName());
        form.setErrorSheetName(profile.getErrorSheetName());
        form.setTaxMode(profile.getTaxMode());
        BigDecimal rate = profile.getTaxRate().stripTrailingZeros();
        form.setTaxRate(rate.scale() < 0 ? rate.setScale(0) : rate);
        form.setAutoEnabled(profile.isAutoEnabled());
        form.setExecutionTime(profile.getExecutionTime());
        form.setTimeZone(profile.getTimeZone());
        ColumnMapping mapping = profile.columnMapping();
        form.setDateColumn(mapping.dateColumn());
        form.setStaffColumn(mapping.staffColumn());
        form.setProductColumn(mapping.productColumn());
        form.setQuantityColumn(mapping.quantityColumn());
        form.setUnitPriceColumn(mapping.unitPriceColumn());
        form.setVersion(profile.getVersion());
        return form;
    }
}

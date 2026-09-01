document.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll("form.confirm-submit").forEach((form) => {
    form.addEventListener("submit", (event) => {
      const message = form.dataset.confirm;
      if (message && !window.confirm(message)) {
        event.preventDefault();
        return;
      }
      setLoading(form);
    });
  });

  document.querySelectorAll("form:not(.confirm-submit)").forEach((form) => {
    form.addEventListener("submit", () => setLoading(form));
  });
});

function setLoading(form) {
  const button = form.querySelector("button[type='submit']");
  if (!button || button.disabled) return;
  button.disabled = true;
  button.textContent = button.dataset.loadingText || "処理中...";
}

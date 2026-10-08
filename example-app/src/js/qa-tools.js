export function openQaToolsPanel() {
  const panel = document.getElementById('qa-tools-panel');
  if (panel) {
    panel.setAttribute('open', '');
  }
}

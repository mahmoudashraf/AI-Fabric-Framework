export function bindWidgetViewport(element: HTMLElement): () => void {
  const view = element.ownerDocument.defaultView;
  if (!view) return () => {};
  const viewport = view.visualViewport;
  const properties = ["top", "left", "width", "height", "bottom", "right"] as const;
  const previous = properties.map((name) => [name, element.style.getPropertyValue(`--max-mode-viewport-${name}`)] as const);
  const update = () => {
    const top = viewport?.offsetTop ?? 0;
    const left = viewport?.offsetLeft ?? 0;
    const width = viewport?.width ?? view.innerWidth;
    const height = viewport?.height ?? view.innerHeight;
    const values = { top, left, width, height,
      bottom: Math.max(0, view.innerHeight - top - height),
      right: Math.max(0, view.innerWidth - left - width) };
    for (const name of properties) {
      element.style.setProperty(`--max-mode-viewport-${name}`, `${values[name]}px`);
    }
  };
  update();
  view.addEventListener("resize", update);
  viewport?.addEventListener("resize", update);
  viewport?.addEventListener("scroll", update);
  return () => {
    view.removeEventListener("resize", update);
    viewport?.removeEventListener("resize", update);
    viewport?.removeEventListener("scroll", update);
    for (const [name, value] of previous) {
      if (value) element.style.setProperty(`--max-mode-viewport-${name}`, value);
      else element.style.removeProperty(`--max-mode-viewport-${name}`);
    }
  };
}

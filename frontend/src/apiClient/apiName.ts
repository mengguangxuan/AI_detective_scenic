// Vite keepNames preserves these class names in the production bundle.
export function apiName(className: string): string {
  return className.replace(/(?:API|Message)$/, '').toLowerCase();
}

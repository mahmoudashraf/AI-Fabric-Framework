declare module '@engage-preview/packs' {
  export function getBundledPreviewPack(code: string): {
    fixtures: (context: Record<string, boolean>) => import('./fixtures').PreviewPackFixtures;
    register: () => void;
  } | undefined;
}

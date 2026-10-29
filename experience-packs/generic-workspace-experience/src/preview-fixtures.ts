/** Synthetic public samples only. No installed knowledge or conversation data. */
export function genericWorkspacePreviewFixtures() {
  return {
    userMessage: 'Can you help me find the available information?',
    answer: 'Here is a summary from the available reference material. You can inspect the sources or attach context to explore further.',
    confirmation: 'Confirm this sample request?',
    documents: [{ id: 'preview-reference', title: 'Sample reference', type: 'document', content: 'Synthetic reference material for preview. No customer knowledge has been loaded.', messageId: 'preview-answer' }],
    attachment: { type: 'document', contextLabel: 'Sample context', data: { id: 'preview-context', title: 'Sample context', name: 'Sample context', content: 'Synthetic attached context.' } },
    fallback: { status: 'Sample result', summary: 'A generic result without a registered presentation.', items: [{ name: 'Reference material', category: 'Knowledge', available: true }] },
    receipt: { reference: 'PREVIEW-001', status: 'Sample only', message: 'No request was submitted.' },
    presentations: [],
  }
}

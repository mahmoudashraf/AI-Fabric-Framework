import type { AssistantOperation, AssistantOperationPage } from './types'

export const operationPageSize = 20
export type OperationPage = AssistantOperationPage & { recordCount: number }
export const uniqueOperations = (operations: AssistantOperation[]) => [...new Map(operations.filter(operation => operation.status === 'EXECUTED').map(operation => [operation.operationId, operation])).values()]

export function readOperationPage(page: AssistantOperationPage, goalId: string, offset: number, limit: number): OperationPage {
  // Do not display another goal's response or use an invalid paging cursor.
  if (page.goalId !== goalId || page.offset !== offset || page.limit !== limit
    || !Number.isSafeInteger(page.total) || page.total < 0 || !Array.isArray(page.operations) || page.operations.length > limit
    || page.operations.length > 0 && offset + page.operations.length > page.total
    || page.operations.some(operation => !operation || typeof operation.operationId !== 'string' || !operation.operationId || typeof operation.status !== 'string')
    || typeof page.hasMore !== 'boolean' || page.hasMore && (!Number.isSafeInteger(page.nextOffset) || page.nextOffset! <= offset || page.nextOffset! >= page.total || page.nextOffset! % limit !== 0)) {
    throw new Error('The receipt page could not be matched to this goal. Refresh the receipts to try again.')
  }
  return { ...page, recordCount: page.operations.length, operations: uniqueOperations(page.operations) }
}

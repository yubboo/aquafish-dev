/** 根据总数和每页数量计算页数，空列表保持一页，便于组件稳定显示。 */
export function getPageCount(total: number, pageSize: number): number {
  const safeTotal = Number.isFinite(total) ? Math.max(0, total) : 0
  const safePageSize = Number.isFinite(pageSize) ? Math.max(1, pageSize) : 1
  return Math.max(1, Math.ceil(safeTotal / safePageSize))
}

/** 把外部传入页码约束到有效范围，避免插件传入异常页码。 */
export function normalizePage(page: number, pageCount: number): number {
  const safePageCount = Number.isFinite(pageCount) ? Math.max(1, Math.floor(pageCount)) : 1
  const safePage = Number.isFinite(page) ? Math.floor(page) : 1
  return Math.min(safePageCount, Math.max(1, safePage))
}

import { describe, expect, it } from 'vitest'

import { getPageCount, normalizePage } from './pagination'

describe('AqPagination 分页边界', () => {
  it('空列表和非法每页数量仍保持稳定的一页', () => {
    expect(getPageCount(0, 20)).toBe(1)
    expect(getPageCount(-1, 0)).toBe(1)
  })

  it('页码不会越过有效范围', () => {
    expect(normalizePage(0, 5)).toBe(1)
    expect(normalizePage(9, 5)).toBe(5)
    expect(normalizePage(3, 5)).toBe(3)
  })
})

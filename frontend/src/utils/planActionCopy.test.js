import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { getPlanActionCopy } from './planActionCopy.js'

describe('plan action copy', () => {
  it('uses goal copy only for create mode', () => {
    assert.equal(getPlanActionCopy('create').label, 'Your Goal')
    assert.equal(getPlanActionCopy('adjust').label, '想调整什么')
    assert.equal(getPlanActionCopy('review').label, '复盘反馈')
  })

  it('falls back to create copy for unknown actions', () => {
    assert.equal(getPlanActionCopy('unknown').submitLabel, '生成计划')
  })
})

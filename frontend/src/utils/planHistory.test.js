import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import {
  createPlanHistoryEntry,
  getPlanProgress,
  refreshPlanHistoryEntry,
  togglePlanNodeCompletion,
  upsertPlanHistory,
  updatePlanProgressFromFeedback,
} from './planHistory.js'

describe('plan history helpers', () => {
  it('keeps only real plan results and preserves every saved plan', () => {
    const first = createPlanHistoryEntry({
      goal: 'Prepare portfolio',
      result: {
        planTitle: 'Portfolio plan',
        planSummary: 'Ship a small portfolio update.',
        phases: [
          { name: 'Collect projects', focus: 'Pick three projects' },
          { name: 'Write case studies', focus: 'Draft short notes' },
        ],
      },
    })

    assert.equal(first.title, 'Portfolio plan')
    assert.equal(first.nodes.length, 2)
    assert.equal(first.nodes[0].completed, false)
    assert.equal(first.result.planTitle, 'Portfolio plan')
    assert.equal(first.result.phases.length, 2)

    const blank = createPlanHistoryEntry({ goal: 'No result', result: {} })
    assert.equal(blank, null)

    const history = Array.from({ length: 8 }, (_, index) =>
      createPlanHistoryEntry({
        goal: `Goal ${index}`,
        result: {
          planTitle: `Plan ${index}`,
          phases: [{ name: `Node ${index}` }],
        },
      }),
    ).reduce((items, entry) => upsertPlanHistory(items, entry), [])

    assert.equal(history.length, 8)
    assert.equal(history[0].title, 'Plan 7')
    assert.equal(history[7].title, 'Plan 0')
  })

  it('updates node completion from user feedback and reports progress', () => {
    const entry = createPlanHistoryEntry({
      goal: 'Prepare portfolio',
      result: {
        planTitle: 'Portfolio plan',
        phases: [
          { name: 'Collect projects', focus: 'Pick three projects' },
          { name: 'Write case studies', focus: 'Draft short notes' },
        ],
      },
    })

    const updated = updatePlanProgressFromFeedback(
      entry,
      '我已经完成 Collect projects，case studies 还没开始。',
    )

    assert.equal(updated.nodes[0].completed, true)
    assert.equal(updated.nodes[1].completed, false)
    assert.equal(getPlanProgress(updated), 50)
  })

  it('matches short Chinese progress feedback against node labels', () => {
    const entry = createPlanHistoryEntry({
      goal: 'Dify 入门',
      result: {
        planTitle: 'Dify 入门体验完成后的下一步',
        phases: [
          { name: '轻量巩固与微实践', focus: '每周花一点时间巩固理解' },
          { name: '考研主线保持', focus: '不干扰主线' },
        ],
      },
    })

    const updated = updatePlanProgressFromFeedback(entry, '刚才已经完成轻量巩固了')

    assert.equal(updated.nodes[0].completed, true)
    assert.equal(updated.nodes[1].completed, false)
    assert.equal(getPlanProgress(updated), 50)
  })

  it('falls back to the next unfinished node for vague completion feedback', () => {
    const entry = createPlanHistoryEntry({
      goal: 'Agent 入门',
      result: {
        planTitle: '转向专注 Agent 开发的低压力入门计划',
        phases: [
          { name: '微实践巩固期' },
          { name: '创建简单问答 Agent' },
        ],
      },
    })

    const firstUpdate = updatePlanProgressFromFeedback(entry, '已经完成了入门教程')
    assert.equal(firstUpdate.nodes[0].completed, true)
    assert.equal(firstUpdate.nodes[1].completed, false)
    assert.equal(getPlanProgress(firstUpdate), 50)

    const secondUpdate = updatePlanProgressFromFeedback(firstUpdate, '这个也做完了')
    assert.equal(secondUpdate.nodes[0].completed, true)
    assert.equal(secondUpdate.nodes[1].completed, true)
    assert.equal(getPlanProgress(secondUpdate), 100)
  })

  it('refreshes an existing plan without losing completed nodes', () => {
    const entry = createPlanHistoryEntry({
      goal: 'Prepare portfolio',
      result: {
        planTitle: 'Portfolio plan',
        phases: [
          { name: 'Collect projects', focus: 'Pick three projects' },
          { name: 'Write case studies', focus: 'Draft short notes' },
        ],
      },
    })

    const refreshed = createPlanHistoryEntry({
      goal: 'Prepare portfolio',
      result: {
        planTitle: 'Portfolio plan v2',
        phases: [
          { name: 'Collect projects', focus: 'Pick best examples' },
          { name: 'Publish portfolio', focus: 'Push the final version' },
        ],
      },
    })

    const merged = refreshPlanHistoryEntry({
      current: entry,
      incoming: refreshed,
      feedback: 'Collect projects finished.',
    })

    assert.equal(merged.id, entry.id)
    assert.equal(merged.title, 'Portfolio plan v2')
    assert.equal(merged.result.planTitle, 'Portfolio plan v2')
    assert.equal(merged.result.phases[1].name, 'Publish portfolio')
    assert.equal(merged.nodes[0].completed, true)
    assert.equal(merged.nodes[1].completed, false)
    assert.equal(getPlanProgress(merged), 50)
  })

  it('toggles a completion node and recalculates plan progress', () => {
    const entry = createPlanHistoryEntry({
      goal: 'Prepare portfolio',
      result: {
        planTitle: 'Portfolio plan',
        phases: [
          { name: 'Collect projects' },
          { name: 'Write case studies' },
        ],
      },
    })

    const checked = togglePlanNodeCompletion(entry, 'node-1')
    assert.equal(checked.nodes[0].completed, true)
    assert.equal(getPlanProgress(checked), 50)

    const unchecked = togglePlanNodeCompletion(checked, 'node-1')
    assert.equal(unchecked.nodes[0].completed, false)
    assert.equal(getPlanProgress(unchecked), 0)
  })
})

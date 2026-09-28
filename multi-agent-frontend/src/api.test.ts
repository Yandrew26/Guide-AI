// Run: npm test  (node's built-in runner; no framework)
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { parseSSE, parseTitleList, toStreamEvent } from './api.ts'

test('parseSSE keeps partial frames for the next chunk', () => {
  const first = parseSSE('data:{"route":"TRAVEL"}\n\ndata:{"rag_chat_st')
  assert.deepEqual(first.data, ['{"route":"TRAVEL"}'])
  const second = parseSSE(first.rest + 'ream":"Hi"}\r\n\r\n')
  assert.deepEqual(second.data, ['{"rag_chat_stream":"Hi"}'])
  assert.equal(second.rest, '')
})

test('stream payloads map to events', () => {
  assert.deepEqual(toStreamEvent({ route: ' BOOK LOOKUP ' }), { kind: 'route', route: 'BOOK LOOKUP' })
  assert.deepEqual(toStreamEvent({ vector_search_stream: '[Dune, Emma, Dune]' }), { kind: 'found', titles: ['Dune', 'Emma'] })
  assert.deepEqual(toStreamEvent({ neo4j_search_stream: '[]' }), { kind: 'related', titles: [] })
  assert.equal(toStreamEvent({ prompt_template_stream: 'You are…' }), null)
})

test('parseTitleList tolerates no brackets', () => {
  assert.deepEqual(parseTitleList('Atomic Habits'), ['Atomic Habits'])
})

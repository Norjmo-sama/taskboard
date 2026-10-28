import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validateTitle } from './validation.mjs';

test('blank rejected', () => assert.equal(validateTitle('').ok, false));
test('whitespace-only rejected', () => assert.equal(validateTitle('   \t\n ').ok, false));
test('81 chars rejected', () => assert.equal(validateTitle('a'.repeat(81)).ok, false));
test('80 chars accepted', () => assert.equal(validateTitle('a'.repeat(80)).ok, true));
test('trims surrounding whitespace', () => assert.deepEqual(validateTitle('  hi  '), { ok: true, title: 'hi' }));
test('81 chars that trim to 80 accepted', () => assert.equal(validateTitle(' ' + 'a'.repeat(80)).ok, true));

#!/usr/bin/env node

// Usage: from the project root, run:
// cat dietwise-architecture/src/scripts/GBD\ Data-SUBSTITUTION_TRAINING.csv | node dietwise-architecture/src/scripts/generate-rule-suggestion-inserts.js

const fs = require('node:fs');
const path = require('node:path');
const { randomUUID } = require('node:crypto');

const EXPECTED_COLUMNS = 12;

function xmlEscape(value) {
	return value
		.replaceAll('&', '&amp;')
		.replaceAll('<', '&lt;')
		.replaceAll('>', '&gt;')
		.replaceAll('"', '&quot;')
		.replaceAll("'", '&apos;');
}

function xmlUnescape(value) {
	return value
		.replaceAll('&quot;', '"')
		.replaceAll('&apos;', "'")
		.replaceAll('&gt;', '>')
		.replaceAll('&lt;', '<')
		.replaceAll('&amp;', '&');
}

function readMappings(changelogsDir) {
	const files = fs.readdirSync(changelogsDir)
		.filter(file => file.endsWith('.xml'))
		.map(file => path.join(changelogsDir, file));

	const maps = {
		DW_RECOMMENDATION: new Map(),
		DW_TRIGGER_INGREDIENT: new Map(),
		DW_ROLE_OR_TECHNIQUE: new Map(),
		DW_ALTERNATIVE_INGREDIENT: new Map(),
	};

	for (const file of files) {
		const content = fs.readFileSync(file, 'utf8');
		const insertRegex = /<insert\s+tableName="([^"]+)">([\s\S]*?)<\/insert>/g;
		let insertMatch;
		while ((insertMatch = insertRegex.exec(content)) !== null) {
			const tableName = insertMatch[1];
			if (!maps[tableName]) {
				continue;
			}
			const body = insertMatch[2];
			const idMatch = body.match(/<column\s+name="id"\s+value="([^"]+)"\s*\/>/);
			const nameMatch = body.match(/<column\s+name="name"\s+value="([^"]*)"\s*\/>/);
			if (!idMatch || !nameMatch) {
				continue;
			}
			maps[tableName].set(xmlUnescape(nameMatch[1]), idMatch[1]);
		}
	}

	return maps;
}

function valueColumn(name, value) {
	if (value === '') {
		return `\t\t\t<column name="${name}" valueComputed="NULL"/>`;
	}
	return `\t\t\t<column name="${name}" value="${xmlEscape(value)}"/>`;
}

function normalizeNullLike(value, nullLiteral) {
	return value.trim().toLowerCase() === nullLiteral.toLowerCase() ? '' : value;
}

function parseInput(stdinText) {
	const lines = stdinText
		.split(/\r?\n/)
		.map(line => line.trim())
		.filter(line => line !== '');

	if (lines.length === 0) {
		throw new Error('No input lines provided on stdin.');
	}

	const first = lines[0].toLowerCase();
	if (first.startsWith('recommendation,')) {
		return lines.slice(1);
	}
	return lines;
}

function requireId(map, name, kind, lineNo) {
	const id = map.get(name);
	if (!id) {
		throw new Error(`Line ${lineNo}: Cannot resolve ${kind} name "${name}" to UUID from existing changelog inserts.`);
	}
	return id;
}

function main() {
	const scriptDir = __dirname;
	const repoRoot = path.resolve(scriptDir, '..', '..', '..');
	const changelogsDir = path.join(repoRoot, 'dietwise-container', 'dietwise-dao-hibernate-reactive', 'src', 'main', 'resources', 'changelogs');

	const mappings = readMappings(changelogsDir);
	const stdinText = fs.readFileSync(0, 'utf8');
	const lines = parseInput(stdinText);

	const out = [];

	for (let i = 0; i < lines.length; i++) {
		const lineNo = i + 1;
		const fields = lines[i].split(',').map(value => value.trim());
		if (fields.length !== EXPECTED_COLUMNS) {
			throw new Error(`Line ${lineNo}: expected ${EXPECTED_COLUMNS} comma-separated fields but got ${fields.length}.`);
		}

		const recommendationName = fields[0];
		const triggerName = fields[1];
		const roleName = fields[2];
		const cuisine = fields[3];
		const equivalence = fields[10];
		const techniqueNotes = fields[11];

		const recommendationId = requireId(mappings.DW_RECOMMENDATION, recommendationName, 'Recommendation', lineNo);
		const triggerId = requireId(mappings.DW_TRIGGER_INGREDIENT, triggerName, 'Trigger Ingredient', lineNo);
		const roleId = requireId(mappings.DW_ROLE_OR_TECHNIQUE, roleName, 'Role or Technique', lineNo);
		const ruleId = randomUUID();

		out.push(`\t\t<insert tableName="DW_RULE">`);
		out.push(`\t\t\t<column name="id" value="${ruleId}"/>`);
		out.push(`\t\t\t<column name="recommendation_id" value="${recommendationId}"/>`);
		out.push(`\t\t\t<column name="trigger_ingredient_id" value="${triggerId}"/>`);
		out.push(`\t\t\t<column name="role_or_technique_id" value="${roleId}"/>`);
		out.push(valueColumn('cuisine', cuisine));
		out.push(`\t\t</insert>`);

		let alternativeOrder = 0;
		for (let altIdx = 0; altIdx < 3; altIdx++) {
			const alternativeName = fields[4 + (altIdx * 2)];
			const restriction = fields[5 + (altIdx * 2)];
			if (!alternativeName) {
				continue;
			}
			const alternativeId = requireId(mappings.DW_ALTERNATIVE_INGREDIENT, alternativeName, `Alternative ${altIdx + 1}`, lineNo);
			const suggestionId = randomUUID();

			out.push('');
			out.push(`\t\t<insert tableName="DW_SUGGESTION_TEMPLATE">`);
			out.push(`\t\t\t<column name="id" value="${suggestionId}"/>`);
			out.push(`\t\t\t<column name="rule_id" value="${ruleId}"/>`);
			out.push(`\t\t\t<column name="alternative_ingredient_id" value="${alternativeId}"/>`);
			out.push(`\t\t\t<column name="alternative_order" valueNumeric="${alternativeOrder}"/>`);
			out.push(valueColumn('restriction', normalizeNullLike(restriction, 'None')));
			out.push(valueColumn('equivalence', normalizeNullLike(equivalence, 'N/A')));
			out.push(valueColumn('technique_notes', normalizeNullLike(techniqueNotes, 'N/A')));
			out.push(`\t\t</insert>`);

			alternativeOrder += 1;
		}

		if (i < lines.length - 1) {
			out.push('');
		}
	}

	process.stdout.write(`${out.join('\n')}\n`);
}

try {
	main();
} catch (err) {
	process.stderr.write(`${err.message}\n`);
	process.exit(1);
}

#!/usr/bin/env node

// Usage: from the project root, run:
// node dietwise-architecture/src/scripts/generate-suggestion-template-translation-inserts.js LT < dietwise-architecture/src/translations/Suggestions_LT.csv

const fs = require('node:fs');
const path = require('node:path');

const EXPECTED_COLUMNS = 6;
const SOURCE_TABLE = 'DW_SUGGESTION_TEMPLATE';
const TRANSLATION_TABLE = 'DW_SUGGESTION_TEMPLATE_TRANSLATION';
const HEADER_FIRST_COLUMN = 'id';

function xmlEscape(value) {
	return value
		.replaceAll('&', '&amp;')
		.replaceAll('<', '&lt;')
		.replaceAll('>', '&gt;')
		.replaceAll('"', '&quot;')
		.replaceAll("'", '&apos;');
}

function readSuggestionTemplateIds(changelogsDir) {
	const files = fs.readdirSync(changelogsDir)
		.filter(file => file.endsWith('.xml'))
		.map(file => path.join(changelogsDir, file));

	const ids = new Set();

	for (const file of files) {
		const content = fs.readFileSync(file, 'utf8');
		const insertRegex = /<insert\s+tableName="([^"]+)">([\s\S]*?)<\/insert>/g;
		let insertMatch;
		while ((insertMatch = insertRegex.exec(content)) !== null) {
			if (insertMatch[1] !== SOURCE_TABLE) {
				continue;
			}
			const body = insertMatch[2];
			const idMatch = body.match(/<column\s+name="id"\s+value="([^"]+)"\s*\/>/);
			if (!idMatch) {
				continue;
			}
			ids.add(idMatch[1]);
		}
	}

	return ids;
}

function parseCsv(text) {
	const rows = [];
	let field = '';
	let row = [];
	let inQuotes = false;
	for (let i = 0; i < text.length; i++) {
		const ch = text[i];
		if (inQuotes) {
			if (ch === '"') {
				if (text[i + 1] === '"') {
					field += '"';
					i++;
				} else {
					inQuotes = false;
				}
			} else {
				field += ch;
			}
		} else if (ch === '"') {
			inQuotes = true;
		} else if (ch === ',') {
			row.push(field);
			field = '';
		} else if (ch === '\r') {
			// swallow
		} else if (ch === '\n') {
			row.push(field);
			rows.push(row);
			field = '';
			row = [];
		} else {
			field += ch;
		}
	}
	if (field !== '' || row.length > 0) {
		row.push(field);
		rows.push(row);
	}
	return rows.filter(r => r.some(f => f.trim() !== ''));
}

function valueColumn(name, value) {
	if (value === '') {
		return `\t\t\t<column name="${name}" valueComputed="NULL"/>`;
	}
	return `\t\t\t<column name="${name}" value="${xmlEscape(value)}"/>`;
}

function requireKnownId(idSet, id, lineNo) {
	if (!idSet.has(id)) {
		throw new Error(`Line ${lineNo}: Suggestion template id "${id}" not found in existing changelog inserts.`);
	}
}

function main() {
	const lang = process.argv[2];
	if (!lang) {
		throw new Error('Usage: generate-suggestion-template-translation-inserts.js <lang> < input.csv');
	}

	const scriptDir = __dirname;
	const repoRoot = path.resolve(scriptDir, '..', '..', '..');
	const changelogsDir = path.join(repoRoot, 'dietwise-container', 'dietwise-dao-hibernate-reactive', 'src', 'main', 'resources', 'changelogs');

	const suggestionTemplateIds = readSuggestionTemplateIds(changelogsDir);
	const stdinText = fs.readFileSync(0, 'utf8');
	const rows = parseCsv(stdinText);

	if (rows.length === 0) {
		throw new Error('No input rows provided on stdin.');
	}

	const hasHeader = rows[0][0].trim().toLowerCase() === HEADER_FIRST_COLUMN;
	const dataRows = hasHeader ? rows.slice(1) : rows;
	const headerOffset = hasHeader ? 2 : 1;

	const out = [];

	for (let i = 0; i < dataRows.length; i++) {
		const lineNo = i + headerOffset;
		const fields = dataRows[i].map(value => value.trim());
		if (fields.length !== EXPECTED_COLUMNS) {
			throw new Error(`Line ${lineNo}: expected ${EXPECTED_COLUMNS} comma-separated fields but got ${fields.length}.`);
		}

		const suggestionTemplateId = fields[0];
		const translatedRestriction = fields[4];
		const translatedEquivalence = fields[5];

		requireKnownId(suggestionTemplateIds, suggestionTemplateId, lineNo);

		out.push(`\t\t<insert tableName="${TRANSLATION_TABLE}">`);
		out.push(`\t\t\t<column name="suggestion_template_id" value="${suggestionTemplateId}"/>`);
		out.push(`\t\t\t<column name="lang" value="${xmlEscape(lang)}"/>`);
		out.push(valueColumn('restriction', translatedRestriction));
		out.push(valueColumn('equivalence', translatedEquivalence));
		out.push(`\t\t</insert>`);

		if (i < dataRows.length - 1) {
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

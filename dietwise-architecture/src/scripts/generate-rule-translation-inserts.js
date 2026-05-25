#!/usr/bin/env node

// Usage: from the project root, run:
// node dietwise-architecture/src/scripts/generate-rule-translation-inserts.js LT < dietwise-architecture/src/translations/Rules_LT.csv

const fs = require('node:fs');
const path = require('node:path');

const EXPECTED_COLUMNS = 6;
const TRANSLATION_TABLE = 'DW_RULE_TRANSLATION';
const HEADER_FIRST_COLUMN = 'recommendation';

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

function readChangelogData(changelogsDir) {
	const files = fs.readdirSync(changelogsDir)
		.filter(file => file.endsWith('.xml'))
		.map(file => path.join(changelogsDir, file));

	const recommendations = new Map();
	const triggers = new Map();
	const roles = new Map();
	const rulesByTriple = new Map();

	const insertRegex = /<insert\s+tableName="([^"]+)">([\s\S]*?)<\/insert>/g;
	const columnRegex = /<column\s+name="([^"]+)"\s+value="([^"]*)"\s*\/>/g;

	for (const file of files) {
		const content = fs.readFileSync(file, 'utf8');
		let insertMatch;
		while ((insertMatch = insertRegex.exec(content)) !== null) {
			const tableName = insertMatch[1];
			const body = insertMatch[2];
			const cols = {};
			columnRegex.lastIndex = 0;
			let colMatch;
			while ((colMatch = columnRegex.exec(body)) !== null) {
				cols[colMatch[1]] = xmlUnescape(colMatch[2]);
			}

			if (tableName === 'DW_RECOMMENDATION' && cols.id && cols.name) {
				recommendations.set(cols.name, cols.id);
			} else if (tableName === 'DW_TRIGGER_INGREDIENT' && cols.id && cols.name) {
				triggers.set(cols.name, cols.id);
			} else if (tableName === 'DW_ROLE_OR_TECHNIQUE' && cols.id && cols.name) {
				roles.set(cols.name, cols.id);
			} else if (tableName === 'DW_RULE' && cols.id && cols.recommendation_id && cols.trigger_ingredient_id && cols.role_or_technique_id) {
				const key = `${cols.recommendation_id}|${cols.trigger_ingredient_id}|${cols.role_or_technique_id}`;
				if (!rulesByTriple.has(key)) {
					rulesByTriple.set(key, []);
				}
				rulesByTriple.get(key).push(cols.id);
			}
		}
	}

	return { recommendations, triggers, roles, rulesByTriple };
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

function requireId(map, name, kind, lineNo) {
	const id = map.get(name);
	if (!id) {
		throw new Error(`Line ${lineNo}: Cannot resolve ${kind} name "${name}" to UUID from existing changelog inserts.`);
	}
	return id;
}

function main() {
	const lang = process.argv[2];
	if (!lang) {
		throw new Error('Usage: generate-rule-translation-inserts.js <lang> < input.csv');
	}

	const scriptDir = __dirname;
	const repoRoot = path.resolve(scriptDir, '..', '..', '..');
	const changelogsDir = path.join(repoRoot, 'dietwise-container', 'dietwise-dao-hibernate-reactive', 'src', 'main', 'resources', 'changelogs');

	const { recommendations, triggers, roles, rulesByTriple } = readChangelogData(changelogsDir);
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

		const recommendationName = fields[1];
		const triggerName = fields[2];
		const roleName = fields[3];
		const translatedRationale = fields[5];

		if (recommendationName === '' || triggerName === '' || roleName === '') {
			process.stderr.write(`Line ${lineNo}: skipping row with empty recommendation/trigger/role.\n`);
			continue;
		}

		const recId = requireId(recommendations, recommendationName, 'Recommendation', lineNo);
		const triggerId = requireId(triggers, triggerName, 'Trigger Ingredient', lineNo);
		const roleId = requireId(roles, roleName, 'Role or Technique', lineNo);

		const key = `${recId}|${triggerId}|${roleId}`;
		const ruleIds = rulesByTriple.get(key);
		if (!ruleIds || ruleIds.length === 0) {
			throw new Error(`Line ${lineNo}: No DW_RULE found for (${recommendationName}, ${triggerName}, ${roleName}).`);
		}
		if (ruleIds.length > 1) {
			throw new Error(`Line ${lineNo}: ${ruleIds.length} DW_RULE rows match (${recommendationName}, ${triggerName}, ${roleName}). Cuisine-based disambiguation is not supported by this script.`);
		}
		const ruleId = ruleIds[0];

		if (out.length > 0) {
			out.push('');
		}
		out.push(`\t\t<insert tableName="${TRANSLATION_TABLE}">`);
		out.push(`\t\t\t<column name="rule_id" value="${ruleId}"/>`);
		out.push(`\t\t\t<column name="lang" value="${xmlEscape(lang)}"/>`);
		out.push(valueColumn('rationale', translatedRationale));
		out.push(`\t\t</insert>`);
	}

	process.stdout.write(`${out.join('\n')}\n`);
}

try {
	main();
} catch (err) {
	process.stderr.write(`${err.message}\n`);
	process.exit(1);
}

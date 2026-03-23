#!/usr/bin/env node

// Usage: from the project root, run:
// node dietwise-architecture/src/scripts/generate-substitution-value-changeset.js

const fs = require('node:fs');
const path = require('node:path');

const CONFIG = {
	recommendationChangelogPath: path.join(
		'dietwise-container',
		'dietwise-dao-hibernate-reactive',
		'src',
		'main',
		'resources',
		'changelogs',
		'20260224_recommendation_data.xml'
	),
	recommendationChangeSetId: 'recommendation_data',
	suggestionChangelogPath: path.join(
		'dietwise-container',
		'dietwise-dao-hibernate-reactive',
		'src',
		'main',
		'resources',
		'changelogs',
		'20260225_suggestion_data.xml'
	),
	suggestionChangeSetId: 'suggestion_data',
	outputChangeSetId: 'substitution_value_data',
	outputAuthor: 'codex',
	csvPath: path.join(
		'dietwise-architecture',
		'src',
		'scripts',
		'GBD Data-SUBSTITUTION_VALUE.csv'
	),
	componentAliases: new Map([
		['nuts_and_seeds', 'nuts and seeds'],
		['whole_grains', 'whole grains'],
		['omega_6_polyunsaturated_fatty_acids', 'omega-6 polyunsaturated fatty acids'],
		['seafood_omega_3_fatty_acids', 'seafood omega-3 fatty acids'],
	]),
};

function xmlEscape(value) {
	return value
		.replaceAll('&', '&amp;')
		.replaceAll('<', '&lt;')
		.replaceAll('>', '&gt;')
		.replaceAll('"', '&quot;')
		.replaceAll("'", '&apos;');
}

function csvValueToString(value) {
	return value.replace(/\r$/, '').trim();
}

function parseCsv(text) {
	const rows = [];
	let row = [];
	let field = '';
	let inQuotes = false;

	for (let i = 0; i < text.length; i++) {
		const char = text[i];
		if (inQuotes) {
			if (char === '"') {
				if (text[i + 1] === '"') {
					field += '"';
					i++;
				} else {
					inQuotes = false;
				}
			} else {
				field += char;
			}
			continue;
		}

		if (char === '"') {
			inQuotes = true;
			continue;
		}

		if (char === ',') {
			row.push(csvValueToString(field));
			field = '';
			continue;
		}

		if (char === '\n') {
			row.push(csvValueToString(field));
			rows.push(row);
			row = [];
			field = '';
			continue;
		}

		field += char;
	}

	if (field.length > 0 || row.length > 0) {
		row.push(csvValueToString(field));
		rows.push(row);
	}

	return rows.filter(parsedRow => parsedRow.some(value => value !== ''));
}

function extractChangeSetContent(xmlText, changeSetId, sourcePath) {
	const changeSetRegex = new RegExp(`<changeSet\\s+id="${changeSetId}"\\s+author="[^"]+">([\\s\\S]*?)</changeSet>`);
	const match = xmlText.match(changeSetRegex);
	if (!match) {
		throw new Error(`Cannot find changeSet "${changeSetId}" in ${sourcePath}.`);
	}
	return match[1];
}

function readInsertMapping(xmlText, changeSetId, tableName, keyColumn, sourcePath) {
	const map = new Map();
	const changeSetContent = extractChangeSetContent(xmlText, changeSetId, sourcePath);
	const insertRegex = new RegExp(`<insert\\s+tableName="${tableName}">([\\s\\S]*?)</insert>`, 'g');
	const columnRegex = /<column\s+name="([^"]+)"\s+value="([^"]+)"\s*\/>/g;
	let insertMatch;

	while ((insertMatch = insertRegex.exec(changeSetContent)) !== null) {
		const columns = new Map();
		let columnMatch;
		while ((columnMatch = columnRegex.exec(insertMatch[1])) !== null) {
			columns.set(columnMatch[1], columnMatch[2]);
		}

		const key = columns.get(keyColumn);
		const id = columns.get('id');
		if (!key || !id) {
			continue;
		}

		map.set(key, id);
	}

	return map;
}

function canonicalComponentName(rawHeader) {
	const trimmedHeader = rawHeader.trim();
	return CONFIG.componentAliases.get(trimmedHeader) ?? trimmedHeader.replaceAll('_', ' ');
}

function requireId(mapping, key, kind) {
	const id = mapping.get(key);
	if (!id) {
		throw new Error(`Cannot resolve ${kind} "${key}" from the configured Liquibase source data.`);
	}
	return id;
}

function createInsertXml(alternativeIngredientId, recommendationId) {
	return [
		'\t\t<insert tableName="DW_ALTERNATIVE_INGREDIENT_COMPONENTS_FOR_SCORING">',
		`\t\t\t<column name="alternative_ingredient_id" value="${xmlEscape(alternativeIngredientId)}"/>`,
		`\t\t\t<column name="recommendation_id" value="${xmlEscape(recommendationId)}"/>`,
		'\t\t</insert>',
	].join('\n');
}

function main() {
	const scriptDir = __dirname;
	const repoRoot = path.resolve(scriptDir, '..', '..', '..');
	const csvPath = path.join(repoRoot, CONFIG.csvPath);
	const recommendationChangelogPath = path.join(repoRoot, CONFIG.recommendationChangelogPath);
	const suggestionChangelogPath = path.join(repoRoot, CONFIG.suggestionChangelogPath);

	const csvText = fs.readFileSync(csvPath, 'utf8').replace(/^\uFEFF/, '');
	const recommendationXml = fs.readFileSync(recommendationChangelogPath, 'utf8');
	const suggestionXml = fs.readFileSync(suggestionChangelogPath, 'utf8');

	const recommendationIdsByComponent = readInsertMapping(
		recommendationXml,
		CONFIG.recommendationChangeSetId,
		'DW_RECOMMENDATION',
		'component_for_scoring',
		recommendationChangelogPath
	);
	const alternativeIngredientIdsByName = readInsertMapping(
		suggestionXml,
		CONFIG.suggestionChangeSetId,
		'DW_ALTERNATIVE_INGREDIENT',
		'name',
		suggestionChangelogPath
	);
	const rows = parseCsv(csvText);

	if (rows.length < 2) {
		throw new Error(`Expected at least one header row and one data row in ${csvPath}.`);
	}

	const headerRow = rows[0];
	if (headerRow.length < 2) {
		throw new Error(`Expected at least two columns in the header row of ${csvPath}.`);
	}

	const recommendationIdsByColumnIndex = headerRow.slice(1).map((headerValue, index) => {
		const componentName = canonicalComponentName(headerValue);
		return {
			columnIndex: index + 1,
			componentName,
			recommendationId: requireId(recommendationIdsByComponent, componentName, 'component_for_scoring'),
		};
	});

	const inserts = [];

	for (let rowIndex = 1; rowIndex < rows.length; rowIndex++) {
		const row = rows[rowIndex];
		const alternativeIngredientName = row[0];
		if (!alternativeIngredientName) {
			throw new Error(`Row ${rowIndex + 1}: missing alternative ingredient name in column 1.`);
		}

		const alternativeIngredientId = requireId(
			alternativeIngredientIdsByName,
			alternativeIngredientName,
			'alternative ingredient name'
		);

		for (const columnInfo of recommendationIdsByColumnIndex) {
			const rawCellValue = row[columnInfo.columnIndex] ?? '';
			const normalizedCellValue = rawCellValue.trim();
			if (normalizedCellValue === '') {
				continue;
			}
			if (normalizedCellValue !== 'X') {
				throw new Error(
					`Row ${rowIndex + 1}, column ${columnInfo.columnIndex + 1}: expected empty or "X" but got "${normalizedCellValue}".`
				);
			}

			inserts.push(createInsertXml(alternativeIngredientId, columnInfo.recommendationId));
		}
	}

	const output = [
		'<?xml version="1.0" encoding="UTF-8"?>',
		'<databaseChangeLog',
		'\t\txmlns="http://www.liquibase.org/xml/ns/dbchangelog"',
		'\t\txmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"',
		'\t\txmlns:ext="http://www.liquibase.org/xml/ns/dbchangelog-ext"',
		'\t\txsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog',
		'\t\thttp://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-5.0.xsd',
		'\t\thttp://www.liquibase.org/xml/ns/dbchangelog-ext',
		'\t\thttp://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-ext.xsd"',
		'>',
		`\t<changeSet id="${CONFIG.outputChangeSetId}" author="${CONFIG.outputAuthor}">`,
	];

	if (inserts.length > 0) {
		output.push(inserts.join('\n'));
	}

	output.push('\t</changeSet>');
	output.push('</databaseChangeLog>');

	process.stdout.write(`${output.join('\n')}\n`);
}

try {
	main();
} catch (error) {
	process.stderr.write(`${error.message}\n`);
	process.exit(1);
}

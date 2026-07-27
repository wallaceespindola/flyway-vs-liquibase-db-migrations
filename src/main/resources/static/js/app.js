/*
 * Flyway vs Liquibase dashboard.
 *
 * Vanilla ES2020 — no framework, no bundler, no dependencies. Everything is driven by four GETs
 * against the app's own REST API.
 */
(() => {
    'use strict';

    const API = {
        comparison: '/api/v1/comparison',
        catalog: engine => `/api/v1/catalog/${engine}`,
    };

    /**
     * Escapes text before it reaches innerHTML. Every string that comes off the API passes through
     * here — the dashboard builds markup with template literals, so this is the single choke point
     * that keeps database content from being parsed as HTML.
     */
    const esc = value => String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');

    /** Coerces a value to a safe integer for interpolation, so counts can never carry markup. */
    const num = value => (Number.isFinite(Number(value)) ? Number(value) : 0);

    const $ = id => document.getElementById(id);

    const fmtDateTime = iso => {
        if (!iso) return '—';
        const d = new Date(iso);
        return Number.isNaN(d.getTime()) ? '—' : d.toLocaleString();
    };

    const fmtDuration = ms => (ms === null || ms === undefined ? 'not recorded' : `${ms} ms`);

    const fmtMoney = value =>
        value === null || value === undefined
            ? '—'
            : Number(value).toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2});

    async function getJson(url) {
        const response = await fetch(url, {headers: {Accept: 'application/json'}});
        const body = await response.json().catch(() => null);
        if (!response.ok || !body || body.success === false) {
            throw new Error(body?.message || `${response.status} ${response.statusText}`);
        }
        return body.data;
    }

    function showError(message) {
        let banner = $('error-banner');
        if (!banner) {
            banner = document.createElement('div');
            banner.id = 'error-banner';
            banner.className = 'error-banner';
            document.querySelector('main').prepend(banner);
        }
        banner.textContent = `Could not load data: ${message}. Is the application running on this port?`;
    }

    function clearError() {
        $('error-banner')?.remove();
    }

    // ---------- renderers ----------

    function renderVerdict(report) {
        const pill = $('verdict-pill');
        const card = $('verdict');
        const body = $('verdict-body');
        const equivalent = report.schemasEquivalent;

        pill.textContent = equivalent ? 'Schemas equivalent' : 'Schemas differ';
        pill.className = `pill ${equivalent ? 'pill-ok' : 'pill-warn'}`;
        card.className = `card verdict-card ${equivalent ? 'is-ok' : 'is-warn'}`;

        const fw = report.flywaySchema;
        const lb = report.liquibaseSchema;

        if (equivalent) {
            body.innerHTML = `
                <p class="verdict-headline">Yes — zero structural differences.</p>
                <p class="verdict-detail">
                    Flyway applied ${num(report.flyway.appliedCount)} migrations and Liquibase applied
                    ${num(report.liquibase.appliedCount)} changesets against separate databases. Comparing
                    ${num(fw.tables.length)} tables, ${num(fw.views.length)} views and
                    ${num(fw.columns.length)} columns read back
                    from <code>INFORMATION_SCHEMA</code>, the two business schemas are identical. The only
                    difference between the databases is the bookkeeping each engine keeps for itself:
                    <code>${esc(fw.bookkeepingTables.join(', '))}</code> versus
                    <code>${esc(lb.bookkeepingTables.join(', '))}</code>.
                </p>`;
            return;
        }

        body.innerHTML = `
            <p class="verdict-headline">No — ${num(report.schemaDifferences.length)} difference(s) found.</p>
            <p class="verdict-detail">The two engines did not converge on the same business schema:</p>
            <ul class="diff-list">
                ${report.schemaDifferences.map(d => `<li>${esc(d)}</li>`).join('')}
            </ul>`;
    }

    function renderEngineSummary(prefix, status) {
        $(`${prefix}-count`).textContent = `${num(status.appliedCount)} applied`;
        $(`${prefix}-last`).textContent = fmtDateTime(status.lastAppliedAt);
        $(`${prefix}-pending`).innerHTML = status.pendingCount === 0
            ? '<span class="badge-yes">none</span>'
            : `<span class="badge-no">${num(status.pendingCount)}</span>`;
    }

    function renderFlywayMigrations(status) {
        const tbody = $('flyway-migrations');
        if (!status.migrations.length) {
            tbody.innerHTML = '<tr><td colspan="5" class="muted">No migrations applied.</td></tr>';
            return;
        }
        tbody.innerHTML = status.migrations.map(m => `
            <tr>
                <td class="id-cell">${esc(m.identifier)}</td>
                <td>${esc(m.description)}</td>
                <td>${esc(m.type)}</td>
                <td>${esc(fmtDateTime(m.appliedAt))}</td>
                <td>${esc(fmtDuration(m.executionTimeMs))}</td>
            </tr>`).join('');
    }

    function renderLiquibaseMigrations(status) {
        const tbody = $('liquibase-migrations');
        if (!status.migrations.length) {
            tbody.innerHTML = '<tr><td colspan="5" class="muted">No changesets applied.</td></tr>';
            return;
        }
        tbody.innerHTML = status.migrations.map(m => `
            <tr>
                <td class="id-cell">${esc(m.identifier.split('::')[0])}</td>
                <td>${esc(m.description)}</td>
                <td>${esc(m.script.split('/').pop())}</td>
                <td>${esc(fmtDateTime(m.appliedAt))}</td>
                <td>${esc(m.author)}</td>
            </tr>`).join('');
    }

    function schemaPanel(snapshot, accentClass) {
        const group = (label, items, tokenClass = 'token') => `
            <div class="group">
                <div class="group-label">${esc(label)} (${num(items.length)})</div>
                <div class="token-list">
                    ${items.length
                        ? items.map(i => `<span class="${tokenClass}">${esc(i)}</span>`).join('')
                        : '<span class="muted">none</span>'}
                </div>
            </div>`;

        // Columns are the noisiest group; summarise per table instead of listing all of them.
        const perTable = snapshot.columns.reduce((acc, entry) => {
            const table = entry.split('.')[0];
            acc[table] = (acc[table] || 0) + 1;
            return acc;
        }, {});
        const columnSummary = Object.entries(perTable).map(([table, count]) => `${table} (${count} cols)`);

        return `
            <div class="schema-panel">
                <h3 class="${accentClass}">${esc(snapshot.engine)}</h3>
                ${group('Tables', snapshot.tables)}
                ${group('Views', snapshot.views)}
                ${group('Columns', columnSummary)}
                ${group('Indexes', snapshot.indexes)}
                ${group('Engine bookkeeping', snapshot.bookkeepingTables, 'token token-bookkeeping')}
            </div>`;
    }

    function renderSchemas(report) {
        $('schema-panels').innerHTML =
            schemaPanel(report.flywaySchema, 'accent-flyway') +
            schemaPanel(report.liquibaseSchema, 'accent-liquibase');
    }

    function renderFeatureMatrix(features) {
        const edgeLabel = {FLYWAY: 'Flyway', LIQUIBASE: 'Liquibase', TIE: 'Tie'};
        $('feature-matrix').innerHTML = features.map(f => `
            <tr>
                <td><strong>${esc(f.feature)}</strong></td>
                <td>${esc(f.flyway)}</td>
                <td>${esc(f.liquibase)}</td>
                <td><span class="edge edge-${esc(f.edge.toLowerCase())}">${esc(edgeLabel[f.edge] || f.edge)}</span></td>
            </tr>`).join('');
    }

    function renderCatalog(products) {
        const tbody = $('catalog-rows');
        if (!products.length) {
            tbody.innerHTML = '<tr><td colspan="6" class="muted">No products.</td></tr>';
            return;
        }
        tbody.innerHTML = products.map(p => `
            <tr>
                <td class="id-cell">${esc(p.sku)}</td>
                <td>${esc(p.productName)}</td>
                <td>${esc(p.categoryName)}</td>
                <td class="num">${esc(fmtMoney(p.price))}</td>
                <td class="num">${esc(p.stockQuantity)}</td>
                <td>${p.active ? '<span class="badge-yes">yes</span>' : '<span class="badge-no">no</span>'}</td>
            </tr>`).join('');
    }

    // ---------- loading ----------

    async function loadCatalog(engine) {
        try {
            renderCatalog(await getJson(API.catalog(engine)));
        } catch (e) {
            $('catalog-rows').innerHTML =
                `<tr><td colspan="6" class="muted">Could not load catalog: ${esc(e.message)}</td></tr>`;
        }
    }

    async function loadAll() {
        const button = $('refresh-btn');
        button.disabled = true;
        try {
            const report = await getJson(API.comparison);
            clearError();
            renderVerdict(report);
            renderEngineSummary('flyway', report.flyway);
            renderEngineSummary('liquibase', report.liquibase);
            renderFlywayMigrations(report.flyway);
            renderLiquibaseMigrations(report.liquibase);
            renderSchemas(report);
            renderFeatureMatrix(report.featureMatrix);
            await loadCatalog(document.querySelector('.tab.is-active').dataset.engine);
        } catch (e) {
            showError(e.message);
            $('verdict-pill').textContent = 'Unavailable';
            $('verdict-pill').className = 'pill pill-warn';
        } finally {
            button.disabled = false;
        }
    }

    // ---------- wiring ----------

    document.addEventListener('DOMContentLoaded', () => {
        $('refresh-btn').addEventListener('click', loadAll);

        document.querySelectorAll('.tab').forEach(tab => {
            tab.addEventListener('click', () => {
                document.querySelectorAll('.tab').forEach(t => {
                    t.classList.remove('is-active');
                    t.setAttribute('aria-selected', 'false');
                });
                tab.classList.add('is-active');
                tab.setAttribute('aria-selected', 'true');
                loadCatalog(tab.dataset.engine);
            });
        });

        loadAll();
    });
})();

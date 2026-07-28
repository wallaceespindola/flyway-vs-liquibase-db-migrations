/*
 * Flyway vs Liquibase dashboard.
 *
 * Vanilla ES2020 — no framework, no bundler, no dependencies. The whole dashboard is driven by two
 * GETs: /api/v1/comparison (which already embeds the feature matrix) and /api/v1/catalog/{engine}.
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
            // role=alert so screen readers announce the failure; the banner sits outside the
            // aria-live region on #verdict, so it needs its own.
            banner.setAttribute('role', 'alert');
            document.querySelector('main').prepend(banner);
        }
        banner.textContent = `Could not load data: ${message}. Is the application running on this port?`;
    }

    /**
     * Replaces every "Loading…" placeholder with the failure reason.
     *
     * Without this, a failed comparison call leaves five regions of the page reading "Loading…"
     * forever, which is indistinguishable from a slow request.
     */
    function markRegionsFailed(message) {
        const note = `Unavailable — ${message}`;

        $('verdict-body').innerHTML = `<p class="verdict-detail">${esc(note)}</p>`;
        [['flyway-migrations', 5], ['liquibase-migrations', 5], ['feature-matrix', 4], ['catalog-rows', 6]]
            .forEach(([id, columns]) => {
                $(id).innerHTML = `<tr><td colspan="${columns}" class="muted">${esc(note)}</td></tr>`;
            });
        $('schema-panels').innerHTML = `<p class="muted">${esc(note)}</p>`;

        ['flyway', 'liquibase'].forEach(prefix => {
            $(`${prefix}-count`).textContent = 'unavailable';
            $(`${prefix}-last`).textContent = '—';
            $(`${prefix}-pending`).textContent = '—';
        });
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

    // Monotonic token so a slow catalog response cannot overwrite a newer one. Clicking Flyway then
    // Liquibase quickly would otherwise render Flyway's rows under the Liquibase tab.
    let catalogRequest = 0;

    async function loadCatalog(engine) {
        const request = ++catalogRequest;
        try {
            const products = await getJson(API.catalog(engine));
            if (request === catalogRequest) {
                renderCatalog(products);
            }
        } catch (e) {
            if (request === catalogRequest) {
                $('catalog-rows').innerHTML =
                    `<tr><td colspan="6" class="muted">Could not load catalog: ${esc(e.message)}</td></tr>`;
            }
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
            $('verdict').className = 'card verdict-card is-warn';
            markRegionsFailed(e.message);
        } finally {
            button.disabled = false;
        }
    }

    // ---------- wiring ----------

    document.addEventListener('DOMContentLoaded', () => {
        $('refresh-btn').addEventListener('click', loadAll);

        const tabs = Array.from(document.querySelectorAll('.tab'));

        const activate = tab => {
            tabs.forEach(t => {
                const selected = t === tab;
                t.classList.toggle('is-active', selected);
                t.setAttribute('aria-selected', String(selected));
                // Roving tabindex: only the selected tab is in the tab order, which is what the
                // tablist pattern expects.
                t.tabIndex = selected ? 0 : -1;
            });
            $('catalog-panel').setAttribute('aria-labelledby', tab.id);
            loadCatalog(tab.dataset.engine);
        };

        tabs.forEach((tab, index) => {
            tab.addEventListener('click', () => activate(tab));

            tab.addEventListener('keydown', event => {
                const offset = {ArrowRight: 1, ArrowLeft: -1, Home: -index, End: tabs.length - 1 - index}[event.key];
                if (offset === undefined) return;
                event.preventDefault();
                const next = tabs[(index + offset + tabs.length) % tabs.length];
                next.focus();
                activate(next);
            });
        });

        loadAll();
    });
})();

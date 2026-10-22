// 코드 화면 공통 스크립트: highlight.js 로 줄 단위 하이라이트를 입힌다.
// 서버가 줄을 나눠서 <td class="src" data-lang="java">줄 내용</td> 로 내려주면,
// 같은 표 안의 .src 칸들을 위에서부터 이어 붙여 한 번에 하이라이트한 뒤 줄별로 다시 나눠 넣는다.
// (여러 줄 주석·문자열도 올바르게 색이 입혀지도록)
(function () {
    var EXT = {
        java: 'java', py: 'python', c: 'c', h: 'c', cpp: 'cpp', cc: 'cpp', hpp: 'cpp', cs: 'csharp',
        js: 'javascript', ts: 'typescript', jsx: 'javascript', html: 'xml', xml: 'xml', css: 'css',
        sql: 'sql', json: 'json', md: 'markdown', sh: 'bash', kt: 'kotlin', go: 'go', rs: 'rust',
        php: 'php', rb: 'ruby', yml: 'yaml', yaml: 'yaml', gradle: 'groovy'
    };

    function langOf(path) {
        var m = /\.([A-Za-z0-9]+)$/.exec(path || '');
        return m ? EXT[m[1].toLowerCase()] : null;
    }

    function escapeHtml(s) {
        return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    // 하이라이트된 HTML 을 줄 단위로 나누되, 줄을 넘어 열려 있는 <span> 은 줄마다 닫고 다시 연다
    function splitLines(html) {
        var open = [];
        return html.split('\n').map(function (line) {
            var prefix = open.join('');
            var re = /<span[^>]*>|<\/span>/g, m;
            while ((m = re.exec(line))) {
                if (m[0].charAt(1) === '/') open.pop(); else open.push(m[0]);
            }
            return prefix + line + '</span>'.repeat(open.length);
        });
    }

    function highlightCells(cells, lang) {
        if (!cells.length) return;
        var text = cells.map(function (c) { return c.textContent; }).join('\n');
        var html;
        try {
            html = (lang && window.hljs && hljs.getLanguage(lang))
                ? hljs.highlight(text, {language: lang, ignoreIllegals: true}).value
                : escapeHtml(text);
        } catch (e) {
            html = escapeHtml(text);
        }
        var lines = splitLines(html);
        cells.forEach(function (c, i) { if (lines[i] !== undefined) c.innerHTML = lines[i] || ' '; });
    }

    window.SubmitHub = {
        langOf: langOf,
        highlightCells: highlightCells,
        // 같은 data-group 을 가진 .src 칸들을 한 파일로 보고 하이라이트
        highlightAll: function (root) {
            var groups = {};
            (root || document).querySelectorAll('td.src[data-group]').forEach(function (td) {
                (groups[td.dataset.group] = groups[td.dataset.group] || {lang: td.dataset.lang, cells: []}).cells.push(td);
            });
            Object.keys(groups).forEach(function (k) { highlightCells(groups[k].cells, groups[k].lang); });
        },
        // 줄 번호(td[data-pick]) 클릭으로 줄/범위(Shift)를 고르고, 그 아래에 코멘트 작성 폼(<template id=tpl>)을 끼워 넣는다
        initCommenting: function (opts) {
            var table = document.getElementById(opts.table);
            var tpl = document.getElementById(opts.tpl);
            if (!table || !tpl) return;
            var cols = parseInt(table.dataset.cols || '2', 10);
            var anchor = null, formRow = null;

            function clear() {
                table.querySelectorAll('tr.picked').forEach(function (r) { r.classList.remove('picked'); });
                if (formRow) { formRow.remove(); formRow = null; }
            }

            table.addEventListener('click', function (e) {
                var td = e.target.closest('td[data-pick]');
                if (!td || !table.contains(td)) return;
                var n = parseInt(td.dataset.pick, 10);
                var start = n, end = n;
                if (e.shiftKey && anchor !== null) {
                    start = Math.min(anchor, n);
                    end = Math.max(anchor, n);
                } else {
                    anchor = n;
                }
                clear();
                var lastRow = null;
                table.querySelectorAll('tr[data-newline]').forEach(function (r) {
                    var ln = parseInt(r.dataset.newline, 10);
                    if (ln >= start && ln <= end) { r.classList.add('picked'); lastRow = r; }
                });
                if (!lastRow) return;
                formRow = document.createElement('tr');
                formRow.className = 'comment-form-row';
                var cell = document.createElement('td');
                cell.colSpan = cols;
                cell.style.background = '#f6f8fa';
                cell.className = 'p-2';
                cell.appendChild(tpl.content.cloneNode(true));
                formRow.appendChild(cell);
                lastRow.after(formRow);
                var form = cell.querySelector('form');
                form.querySelector('[name=startLine]').value = start;
                form.querySelector('[name=endLine]').value = end;
                form.querySelector('.range').textContent = start === end ? '줄 ' + start : '줄 ' + start + '~' + end;
                form.querySelector('.cancel').addEventListener('click', clear);
                form.querySelector('textarea').focus();
                e.preventDefault();
            });
            // 선택 시 글자가 같이 선택되는 것을 막는다 (Shift+클릭)
            table.addEventListener('mousedown', function (e) {
                if (e.shiftKey && e.target.closest('td[data-pick]')) e.preventDefault();
            });
        },
        csrfHeaders: function () {
            var t = document.querySelector('meta[name=_csrf]');
            var h = document.querySelector('meta[name=_csrf_header]');
            var headers = {};
            if (t && h) headers[h.content] = t.content;
            return headers;
        }
    };

    document.addEventListener('DOMContentLoaded', function () { SubmitHub.highlightAll(); });
})();

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
将用友 NC65 官方 CHM 数据字典（hh.exe -decompile 解出来的 HTML 目录）
解析为紧凑的补充字典 JSON，供导出工具在数据库元数据缺失中文名时兜底。

用法:
    python chm_to_json.py --src <解包目录> --out <输出.json>

输出结构（表名/字段名统一转大写，便于匹配）:
{
  "BD_DEFDOC": { "n": "供应商资料类型(自定义档案)", "f": { "CODE": "档案编码", ... } },
  ...
}
"""
import argparse
import glob
import html.parser
import json
import re
import sys

TITLE_RE = re.compile(r'<b><span style="font-size:24px">(.*?)</span></b>', re.S)
TABLE_RE = re.compile(r'\(([^()]*)\)\s*$')


class TableRows(html.parser.HTMLParser):
    """提取页面第一个表格的行（每行 = 单元格文本列表），忽略单元格内嵌套标签。"""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.rows = []
        self.in_table = 0
        self.row = None
        self.cell = None
        self.cell_depth = 0

    def handle_starttag(self, tag, attrs):
        if tag == "table":
            self.in_table += 1
            self.row = None
            self.cell = None
        elif self.in_table:
            if tag == "tr":
                if self.row is not None:
                    self.rows.append(self.row)
                self.row = []
                self.cell = None
            elif tag in ("td", "th") and self.row is not None:
                self.cell = []
                self.cell_depth = 1
            elif self.cell is not None:
                if tag == "br":
                    self.cell.append("\n")
                else:
                    self.cell_depth += 1

    def handle_endtag(self, tag):
        if tag == "table" and self.in_table:
            self.in_table -= 1
            if self.row is not None and self.row:
                self.rows.append(self.row)
            self.row = None
            self.cell = None
            self.cell_depth = 0
        elif not self.in_table or self.cell is None:
            return
        elif tag == "tr":
            if self.row is not None and self.row:
                self.rows.append(self.row)
            self.row = []
            self.cell = None
            self.cell_depth = 0
        elif tag in ("td", "th"):
            if self.cell_depth > 1:
                self.cell_depth -= 1
            elif self.row is not None:
                self.row.append("".join(self.cell))
                self.cell = None
                self.cell_depth = 0
        elif self.cell is not None:
            self.cell_depth -= 1

    def handle_data(self, data):
        if self.cell is not None:
            self.cell.append(data)


def clean(s):
    s = s.replace("\xa0", " ").replace("&nbsp;", " ")
    s = re.sub(r"\s+", " ", s).strip()
    return s


def parse_page(path):
    """返回 (tableName, chineseName, {fieldCode: fieldCn})，非实体页返回 None"""
    try:
        with open(path, "rb") as fh:
            data = fh.read().decode("gb18030", "replace")
    except OSError:
        return None

    m = TITLE_RE.search(data)
    if not m:
        return None
    big = clean(m.group(1))
    tm = TABLE_RE.search(big)
    if not tm:
        return None
    table = clean(tm.group(1))
    cn = big[: tm.start()].strip().rstrip("(").strip()
    if not table or not cn:
        return None

    parser = TableRows()
    try:
        parser.feed(data)
    except Exception:
        return None

    fields = {}
    for row in parser.rows:
        cells = [clean(c) for c in row]
        if not cells or cells[0] == "序号":
            continue
        if len(cells) < 3:
            continue
        code, name = cells[1], cells[2]
        if not code or not name or code == "编码":
            continue
        fields[code.upper()] = name

    if not fields:
        return None
    return table.upper(), cn, fields


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True, help="CHM 解包出的 HTML 目录")
    ap.add_argument("--out", required=True, help="输出 JSON 路径")
    args = ap.parse_args()

    result = {}
    got = 0
    missed = 0

    files = sorted(glob.glob(args.src.rstrip("\\/") + "/*.html"))

    def is_canonical(cn):
        # 中文名里没有额外括号分组（如“自定义档案” vs “供应商资料类型(自定义档案)”）
        # 说明这一页就是表的规范章节页，而不是某个自定义档案/分类的实例页
        return "(" not in cn

    def better(a, b):
        # 比较两个 (cn, fields) 哪个更适合代表这张表
        # 优先规范页（无括号分类），其次字段更全
        ca, fa = a
        cb, fb = b
        if is_canonical(ca) != is_canonical(cb):
            return a if is_canonical(ca) else b
        return a if len(fa) >= len(fb) else b

    for i, path in enumerate(files, 1):
        parsed = parse_page(path)
        if parsed:
            table, cn, fields = parsed
            prev = result.get(table)
            if prev is not None:
                cur = (prev["n"], prev["f"])
                if better(cur, (cn, fields)) is cur:
                    got += 1
                    continue
            result[table] = {"n": cn, "f": fields}
            got += 1
        else:
            missed += 1
        if i % 500 == 0:
            print(f"  ... {i}/{len(files)}", file=sys.stderr)

    with open(args.out, "w", encoding="utf-8") as fh:
        json.dump(result, fh, ensure_ascii=False, separators=(",", ":"))

    total_fields = sum(len(v["f"]) for v in result.values())
    size = __import__("os").path.getsize(args.out)
    print(f"HTML 共 {len(files)} 个；实体页 {got} 个(另有 {missed} 个非实体/解析失败)；")
    print(f"字段条目 {total_fields}；输出 {args.out}（{size / 1024:.0f} KB）")


if __name__ == "__main__":
    main()

from pathlib import Path
import json
root=Path(__file__).resolve().parent
ckb=json.loads((root/'ckb.json').read_text())
rows={}
for line in (root/'translations.tsv').read_text().splitlines():
 n,en,ar=line.split('|');rows[int(n)]=[en.replace('\\n','\n'),ar.replace('\\n','\n')]
assert set(rows)==set(range(len(ckb)))
ckb[0]='٩.١ • بێ ئینتەرنێت'
ckb[63]='QR ی ناو ئەپی rebaritclone ی مۆبایلی نوێ سکان بکە؛ ئەپەکە لە هەردوو مۆبایل نوێ بکەرەوە.'
ckb[30]='SMS و مێژووی پەیوەندی بە یەک کرتە هەمووی هەڵدەبژێردرێت؛ کرتەی دووەم لایاندەبات. ئەپ و ژمارەکان تاک‌تاک هەڵبژێرە.'
ckb[233]='یەک کرتە: هەموو پەیوەندییە بەردەستەکان — فایلێکی پاشەکەوت'
ckb[263]='وێنە، ڤیدیۆ، دەنگ، ئەپ و فایلەکان بۆ ناردن هەڵبژێرە' 
ckb[53]='١. لە مۆبایلی وەرگر «مۆبایلی نوێ» دابگرە.\n٢. QR ی ئەو مۆبایلە سکان بکە.\n٣. داواکاری پەیوەستبوونی ئەندرۆید قبووڵ بکە.\n\nسیمکارت، داتا و ئینتەرنێت پێویست نین.'
ckb[275]='٢  لە مۆبایلە کۆنەکە ناردن بە USB-C هەڵبژێرە؛ دوای پەیوەندی داتا دیاری بکە.'
# Arabic fragments retain spaces at both ends when inserted between numbers.
rows[139][1]=' ساعة و ';rows[141][1]=' دقيقة و '
lines=['package com.abdulla.clonephone;','/** Generated from localization catalogs. Do not hand-edit. */','public final class Translations {','    public static final int COUNT='+str(len(ckb))+';','    private static final String[][] TEXT={']
for i,k in enumerate(ckb):lines.append('        {'+','.join(json.dumps(v,ensure_ascii=False) for v in [k,*rows[i]])+'},')
lines+=['    };','    public static String get(String language,int id){return TEXT[id]["en".equals(language)?1:"ar".equals(language)?2:0];}','    public static boolean rtl(String language){return !"en".equals(language);}','}']
(root.parent/'app/src/main/java/com/abdulla/clonephone/Translations.java').write_text('\n'.join(lines)+'\n')

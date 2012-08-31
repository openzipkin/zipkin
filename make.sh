redcarpet --parse-fenced_code_blocks src/index.md > tmp
cat src/head tmp src/tail > index.html
rm tmp

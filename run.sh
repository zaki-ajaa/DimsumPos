#!/bin/sh
# Jalankan dari folder proyek:  sh run.sh        (tes: sh run.sh tes)
cd "$(dirname "$0")" || exit 1
rm -rf out
javac -d out $(find src -name '*.java') || exit 1
if [ "$1" = "tes" ]; then
  java -cp out kasir.TestKasir
else
  java -cp out kasir.Main
fi

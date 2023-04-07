#!/bin/sh

log=$*

./scripts/process-log.pl $log > F3
grep 'cur_mu' $log > F4
grep '^CRT.*count' $log > CRT
grep '^RT.*count' $log > RT
grep '^TST.*count' $log > TST
grep '^AA.*count' $log > AA
grep '^IA<.*count' $log > IA
grep '^ST.*count' $log > ST
grep '^SC.*count' $log > SC
grep '^INJRT.*count' $log > INJRT
grep '^IAT.*count' $log > IAT

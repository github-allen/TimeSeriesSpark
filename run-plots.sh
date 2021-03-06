#!/bin/bash
# Script for Carat data analysis to plots instead of the database.
if [ ! -d logs ]; then mkdir logs; fi

d=$( date +%Y-%m-%d-%H-%M-%S )
echo "Plotting started at $d"
./run-laptop.sh edu.berkeley.cs.amplab.carat.PlotAndMakeAvailable \
&> "logs/carat-plots-log-$d.txt"
d=$( date +%Y-%m-%d-%H-%M-%S )
echo "Plotting finished at $d"
echo "Removing temporary files"
rm -rf spark-temp-plots/spark-local-*


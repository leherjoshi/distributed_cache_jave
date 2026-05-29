#!/bin/bash

echo "Starting Distributed Cache System Demo..."
echo ""

mvn exec:java -Dexec.mainClass="com.distributedcache.demo.QuickStartDemo" -q

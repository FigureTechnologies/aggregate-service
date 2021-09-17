#!/usr/bin/env python

# This file is intended to be used to read Java .properties files. There's no dedicated Python
# library for reading that format, however the .properties format is very similar to the .ini format.
# Adding the "[default]" header allows us to use the `configparser` stdlib functionality in Python to
# to read properties from a property file

import configparser
from pathlib import Path
import sys

if len(sys.argv) < 3:
    raise Exception("Expected <property-file-name> <property-name>")

prop_file_path = sys.argv[1]
prop_name = sys.argv[2]

prop_file = Path(prop_file_path)
contents = "[default]\n" + (open(prop_file, 'r').read())
config = configparser.ConfigParser()
config.read_string(contents)
print(config['default'].get(prop_name, '').strip())

#!/usr/bin/env python3
"""Ensure pilot networking never expands to release or SMS permissions."""
import argparse
import re
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--aapt', required=True)
parser.add_argument('--debug', required=True)
parser.add_argument('--release', required=True)
args = parser.parse_args()
for variant in ('debug', 'release'):
    output = subprocess.check_output([args.aapt, 'dump', 'permissions', getattr(args, variant)], text=True)
    permissions = set(re.findall(r"uses-permission: name='([^']+)'", output))
    forbidden = {'android.permission.SEND_SMS', 'android.permission.READ_SMS', 'android.permission.RECEIVE_SMS'}
    assert not permissions & forbidden, 'SMS permission found'
    assert ('android.permission.INTERNET' in permissions) == (variant == 'debug'), 'Incorrect pilot networking permission'
    print(f'PASS: {variant} permissions')

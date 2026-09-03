import re
lines = open('BabyRatMicro.java').read().split('\n')
def replace(match):
    # print(match.group(0))
    return f"""{match.group(1)}if ({match.group(2)}.score > curActionScoreScore) {{
{match.group(1)}\tcurActionScoreScore = {match.group(2)}.score;
{match.group(1)}\tcurActionScoreDir = {match.group(2)}.dir;
{match.group(1)}\tcurActionScoreType = {match.group(2)}.type;
{match.group(1)}\tcurActionScoreTarget = {match.group(2)}.target;
{match.group(1)}}}"""
for i, val in enumerate(lines):
    val = re.sub(r'(\s+)curActionScore.compare\((attackScore[0-9])\);', replace, val)
    lines[i] = val
print('\n'.join(lines))
from util import *
import math
p = Printer(2)
actualPrint = print
print = p.print

def printRuinSeenLocsBitmasks(maxDist=25):
    p.tabs = 2
    dy = math.isqrt(maxDist)
    print("switch (loc.y) {")
    p.tabs += 1
    for y in range(60):
        print(f"case {y}:")
        p.tabs += 1
        for curY in range(max(0, y-dy), min(59, y+dy)+1):
            dx = math.isqrt(maxDist - (y-curY)*(y-curY))
            print(f"catSeenLocs[{curY}] |= 0b{'1'*(dx*2+1)} << loc.x - {dx};")
        print("break;")
        p.tabs -= 1
    p.tabs -= 1
    print("}")
printRuinSeenLocsBitmasks()
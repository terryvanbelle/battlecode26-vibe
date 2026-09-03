from util import *
import math
p = Printer(2)
actualPrint = print
print = p.print

def printCatSeenLocsBitmasks(maxDist=25):
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
# printCatSeenLocsBitmasks()

def printUpdateExploredBabyRat():
    p.tabs = 1
    print("public static void updateExploredBabyRat() {")
    p.tabs += 1
    print("switch (G.dir) {")
    p.tabs += 1
    for dir in range(8):
        print(f"case {ALL_DIRECTIONS[dir]}:")
        p.tabs += 1
        print(f"updateExploredBabyRat{dir}();")
        print("break;")
        p.tabs -= 1
    p.tabs -= 1
    print("}")
    p.tabs -= 1
    print("}")

    for dir in range(8):
        print(f"public static void updateExploredBabyRat{dir}() {{")
        p.tabs += 1
        print("switch (G.me.y) {")
        p.tabs += 1
        for y in range(60):
            print(f"case {y}:")
            p.tabs += 1
            visibleLocs = [0] * 9
            for i in getAllVisibleLocs(dir):
                visibleLocs[i.y + 4] |= (1 << i.x + 4)
            for i in range(9):
                if y + i - 4 < 0 or y + i - 4 > 59 or visibleLocs[i] == 0: continue
                binary = bin(visibleLocs[i])
                trailingZeros = binary.count('0') - 1
                if trailingZeros > 0:
                    binary = binary[:-trailingZeros]
                print(f"explored[{y + i - 4}] |= {binary} << G.me.x{"" if trailingZeros == 4 else f" - {4-trailingZeros}"};")
            print("break;")
            p.tabs -= 1
        p.tabs -= 1
        print("}")
        p.tabs -= 1
        print("}")
printUpdateExploredBabyRat()

def printUpdateExploredRatKing():
    p.tabs = 1
    print("public static void updateExploredRatKing() {")
    p.tabs += 1
    print("switch (G.me.y) {")
    p.tabs += 1
    for y in range(60):
        print(f"case {y}:")
        p.tabs += 1
        visibleLocs = [0] * 11
        for i in getAllLocs(25):
            visibleLocs[i.y + 5] |= (1 << i.x + 5)
        for i in range(11):
            if y + i - 5 < 0 or y + i - 5 > 59 or visibleLocs[i] == 0: continue
            binary = bin(visibleLocs[i])
            trailingZeros = binary.count('0') - 1
            if trailingZeros > 0:
                binary = binary[:-trailingZeros]
            print(f"explored[{y + i - 5}] |= {binary} << G.me.x{"" if trailingZeros == 5 else f" - {5-trailingZeros}"};")
        print("break;")
        p.tabs -= 1
    p.tabs -= 1
    print("}")
    p.tabs -= 1
    print("}")
printUpdateExploredRatKing()
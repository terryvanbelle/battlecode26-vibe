from util import *
import math
p = Printer(2)
actualPrint = print
print = p.print

def printAttemptSpawn():
    print("public static void attemptSpawn() throws Exception {")
    p.tabs += 1
    print("Direction spawnDir;")
    print("MapLocation spawnLoc;")
    print("double bestScore = 0;")
    for i in getAllLocs(8):
        if i not in dxy:
            print(f"double score{locToIndex(i)} = 100 - Math.sqrt(G.me.translate({i}).distanceSquaredTo(G.mapCenter));")
            print(f"if (G.rc.canBuildRat(G.me.translate({i}))) {{")
            p.tabs += 1
            
            p.tabs -= 1
            print("} else {")
            p.tabs += 1
            print(f"score{locToIndex(i)} = -100000;")
            p.tabs -= 1
            print("}")
    p.tabs -= 1
    print("}")
printAttemptSpawn()
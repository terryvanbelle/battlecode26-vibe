from util import *
p = Printer(2)
print = p.print

def printTrapScore():
    p.tabs = 2
    def printTrapScoreDir(locVar):
        print(f"if (G.rc.canSenseLocation({locVar})) {{")
        p.tabs += 1
        print(f"if (G.rc.senseMapInfo({locVar}).getTrap() == TrapType.RAT_TRAP) {{")
        p.tabs += 1
        print("score -= 200;")
        p.tabs -= 1
        print("}")
        p.tabs -= 1
        print("}")
        print(f"if (G.opponentRobots.exists({locVar})) {{")
        p.tabs += 1
        print(f"score += 50 * scorePerOpponentHp - G.opponentRobots.get({locVar}).health / 10000;")
        p.tabs -= 1
        print("}")
    for dir in range(8):
        print(f"MapLocation loc{dir} = loc.add({ALL_DIRECTIONS[dir]});")
        printTrapScoreDir(f"loc{dir}")
    printTrapScoreDir("loc")
# printTrapScore()

def printCheeseAttackAmount():
    p.tabs = 5
    print("switch (robot.health) {")
    p.tabs += 1
    cheeseAmount = [0] * 101
    for hp in range(0, 101):
        if hp >= 10:
            if hp % 10 == 1:
                cheeseAmount[hp] = 1
            elif hp % 10 == 2:
                cheeseAmount[hp] = 2
            # elif hp % 10 == 3:
            #     cheeseAmount[hp] = 3
            else:
                cheeseAmount[hp] = 0
        else:
            cheeseAmount[hp] = 0
    for hp in range(0, 101):
        print(f"case {hp}:")
        p.tabs += 1
        if hp == 100 or cheeseAmount[hp] != cheeseAmount[hp+1]:
            if cheeseAmount[hp] != 0:
                print(f"attackType = MicroActionScore.ATTACK_{cheeseAmount[hp]};")
            print("break;")
        p.tabs -= 1
    p.tabs -= 1
    print("}")
printCheeseAttackAmount()
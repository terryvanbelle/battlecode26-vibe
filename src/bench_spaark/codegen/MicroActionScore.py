from util import *
p = Printer(1)
print = p.print

def printMicroActionScoreCheeseVariables():
    for dmg in range(1, 10):
        print(f"public static final int ATTACK_{dmg} = {dmg + 4}; // {(dmg-1)**2 + 1} cheese")
printMicroActionScoreCheeseVariables()
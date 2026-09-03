class Printer:
    def __init__(self, tabs=0):
        self.tabs = tabs
    def print(self, out):
        print('\t'*self.tabs + out)


class MapLocation():
    def __init__(self, x, y):
        self.x=x
        self.y=y
    
    def __add__(self, o):
        return MapLocation(self.x + o.x, self.y + o.y)
    
    def __sub__(self, o):
        return MapLocation(self.x - o.x, self.y - o.y)
    
    def __str__(self):
        return f'{self.x}, {self.y}'
    
    def __hash__(self):
        return hash((self.x << 32) + self.y)

    def __eq__(self, o):
        return isinstance(o, MapLocation) and self.x == o.x and self.y == o.y
    
ALL_DIRECTIONS = [
    'Direction.SOUTHWEST',
    'Direction.SOUTH',
    'Direction.SOUTHEAST',
    'Direction.EAST',
    'Direction.NORTHEAST',
    'Direction.NORTH',
    'Direction.NORTHWEST',
    'Direction.WEST',
    'Direction.CENTER'
]
dxy = [
    MapLocation(-1, -1),
    MapLocation(0, -1),
    MapLocation(1, -1),
    MapLocation(1, 0),
    MapLocation(1, 1),
    MapLocation(0, 1),
    MapLocation(-1, 1),
    MapLocation(-1, 0),
    MapLocation(0, 0)
]

_indexToLoc = {}
_locToIndex = {}
all_locs = []
for x in range(-10, 10):
    for y in range(-10, 10):
        all_locs.append((x, y))
all_locs.sort(key=lambda a:a[0]*a[0]+a[1]*a[1])
for ind, val in enumerate(all_locs):
    _indexToLoc[ind] = val
    _locToIndex[val] = ind
all_locs = [MapLocation(i[0], i[1]) for i in all_locs]

def indexToLoc(i):
    return MapLocation(_indexToLoc[i][0], _indexToLoc[i][1])
def locToIndex(m):
    return _locToIndex[(m.x, m.y)]
def getAllVisibleLocs(dir, range=20):
    # where dir is in [0, 7]
    dx = dxy[dir].x
    dy = dxy[dir].y
    locs = []
    for i in all_locs:
        if i.x**2 + i.y**2 > range:
            break
        if i.x == 0 and i.y == 0:
            locs.append(i)
            continue
        dot = i.x * dx + i.y * dy
        if dot > 0:
            cosSquared = dot * dot / (dx * dx + dy * dy) / (i.x * i.x + i.y * i.y)
            if cosSquared >= 0.49999:
                locs.append(i)
    return locs
def getAllLocs(range=20):
    locs = []
    for i in all_locs:
        if i.x**2 + i.y**2 > range:
            break
        locs.append(i)
    return locs

scorePerSelfHp = 10
scorePerOpponentHp = 10
scorePerCatHp = 1
scorePerCheese = 20
scorePerMoveCooldown = 5
scorePerActionCooldown = 5
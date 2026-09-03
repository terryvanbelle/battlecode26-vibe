package bench_stroke.Pathfinding;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;

import static bench_stroke.RobotPlayer.rc;

public class RatKingBFS {
    static MapLocation l0;
    static int dist0;
    static int h0;
    static boolean exists0;
    static int cost0;

    static MapLocation l1;
    static int dist1;
    static int h1;
    static boolean exists1;
    static int cost1;

    static MapLocation l2;
    static int dist2;
    static int h2;
    static boolean exists2;
    static int cost2;

    static MapLocation l3;
    static int dist3;
    static int h3;
    static boolean exists3;
    static int cost3;

    static MapLocation l4;
    static int dist4;
    static int h4;
    static boolean exists4;
    static int cost4;

    static MapLocation l5;
    static int dist5;
    static int h5;
    static boolean exists5;
    static int cost5;

    static MapLocation l6;
    static int dist6;
    static int h6;
    static boolean exists6;
    static int cost6;

    static MapLocation l7;
    static int dist7;
    static int h7;
    static boolean exists7;
    static int cost7;

    static MapLocation l8;
    static int dist8;
    static int h8;
    static boolean exists8;
    static int cost8;

    static MapLocation l9;
    static int dist9;
    static int h9;
    static boolean exists9;
    static int cost9;

    static MapLocation l10;
    static int dist10;
    static int h10;
    static boolean exists10;
    static int cost10;

    static MapLocation l11;
    static int dist11;
    static int h11;
    static boolean exists11;
    static int cost11;

    static MapLocation l12;
    static int dist12;
    static int h12;
    static boolean exists12;
    static int cost12;

    static MapLocation l13;
    static int dist13;
    static int h13;
    static boolean exists13;
    static int cost13;

    static MapLocation l14;
    static int dist14;
    static int h14;
    static boolean exists14;
    static int cost14;

    static MapLocation l15;
    static int dist15;
    static int h15;
    static boolean exists15;
    static int cost15;

    static MapLocation l16;
    static int dist16;
    static int h16;
    static boolean exists16;
    static int cost16;

    static MapLocation l17;
    static int dist17;
    static int h17;
    static boolean exists17;
    static int cost17;

    static MapLocation l18;
    static int dist18;
    static int h18;
    static boolean exists18;
    static int cost18;

    static MapLocation l19;
    static int dist19;
    static int h19;
    static boolean exists19;
    static int cost19;

    static MapLocation l20;
    static int dist20;
    static int h20;
    static boolean exists20;
    static int cost20;

    static MapLocation l21;
    static int dist21;
    static int h21;
    static boolean exists21;
    static int cost21;

    static MapLocation l22;
    static int dist22;
    static int h22;
    static boolean exists22;
    static int cost22;

    static MapLocation l23;
    static int dist23;
    static int h23;
    static boolean exists23;
    static int cost23;

    static MapLocation l24;
    static int dist24;
    static int h24;
    static boolean exists24;
    static int cost24;

    static MapLocation l25;
    static int dist25;
    static int h25;
    static boolean exists25;
    static int cost25;

    static MapLocation l26;
    static int dist26;
    static int h26;
    static boolean exists26;
    static int cost26;

    static MapLocation l27;
    static int dist27;
    static int h27;
    static boolean exists27;
    static int cost27;

    static MapLocation l28;
    static int dist28;
    static int h28;
    static boolean exists28;
    static int cost28;

    static MapLocation l29;
    static int dist29;
    static int h29;
    static boolean exists29;
    static int cost29;

    static MapLocation l30;
    static int dist30;
    static int h30;
    static boolean exists30;
    static int cost30;

    static MapLocation l31;
    static int dist31;
    static int h31;
    static boolean exists31;
    static int cost31;

    static MapLocation l32;
    static int dist32;
    static int h32;
    static boolean exists32;
    static int cost32;

    static MapLocation l33;
    static int dist33;
    static int h33;
    static boolean exists33;
    static int cost33;

    static MapLocation l34;
    static int dist34;
    static int h34;
    static boolean exists34;
    static int cost34;

    static MapLocation l35;
    static int dist35;
    static int h35;
    static boolean exists35;
    static int cost35;

    static MapLocation l36;
    static int dist36;
    static int h36;
    static boolean exists36;
    static int cost36;

    static MapLocation l37;
    static int dist37;
    static int h37;
    static boolean exists37;
    static int cost37;

    static MapLocation l38;
    static int dist38;
    static int h38;
    static boolean exists38;
    static int cost38;

    static MapLocation l39;
    static int dist39;
    static int h39;
    static boolean exists39;
    static int cost39;

    static MapLocation l40;
    static int dist40;
    static int h40;
    static boolean exists40;
    static int cost40;

    static MapLocation l41;
    static int dist41;
    static int h41;
    static boolean exists41;
    static int cost41;

    static MapLocation l42;
    static int dist42;
    static int h42;
    static boolean exists42;
    static int cost42;

    static MapLocation l43;
    static int dist43;
    static int h43;
    static boolean exists43;
    static int cost43;

    static MapLocation l44;
    static int dist44;
    static int h44;
    static boolean exists44;
    static int cost44;

    static MapLocation l45;
    static int dist45;
    static int h45;
    static boolean exists45;
    static int cost45;

    static MapLocation l46;
    static int dist46;
    static int h46;
    static boolean exists46;
    static int cost46;

    static MapLocation l47;
    static int dist47;
    static int h47;
    static boolean exists47;
    static int cost47;

    static MapLocation l48;
    static int dist48;
    static int h48;
    static boolean exists48;
    static int cost48;

    static Direction[][] lookup;

    public static MapLocation getLocationFromIndex(int nodeIndex, MapLocation center) {
        return new MapLocation(center.x - 3 + nodeIndex % 7, center.y - 3 + 6 - nodeIndex / 7);
    }

    public static int indexToLocalX(int nodeIndex) {
        return nodeIndex % 7;
    }

    public static int indexToLocalY(int nodeIndex) {
        return nodeIndex / 7;
    }

    public static Direction dirToMove(int nodeIndex) {
        int currentNodeIndex = nodeIndex;
        Direction currentDirection = lookup[indexToLocalY(nodeIndex)][indexToLocalX(nodeIndex)];
        while (currentDirection != null) {
            Direction tempDirection = currentDirection;
            switch (currentDirection) {
                case NORTH:
                    currentNodeIndex = currentNodeIndex - 7;
                    break;
                case NORTHEAST:
                    currentNodeIndex = currentNodeIndex - 6;
                    break;
                case EAST:
                    currentNodeIndex = currentNodeIndex + 1;
                    break;
                case SOUTHEAST:
                    currentNodeIndex = currentNodeIndex + 8;
                    break;
                case SOUTH:
                    currentNodeIndex = currentNodeIndex + 7;
                    break;
                case SOUTHWEST:
                    currentNodeIndex = currentNodeIndex + 6;
                    break;
                case WEST:
                    currentNodeIndex = currentNodeIndex - 1;
                    break;
                case NORTHWEST:
                    currentNodeIndex = currentNodeIndex - 8;
                    break;
            }
            currentDirection = lookup[currentNodeIndex / 7][currentNodeIndex % 7];
            if (currentDirection == null) return tempDirection.opposite();
        }
        return null;
    }

    public static Direction pathfind(MapLocation destination) throws GameActionException {
        MapLocation start = rc.getLocation();
        lookup = new Direction[7][7];

        l0 = getLocationFromIndex(0, start);
        dist0 = 1000000;
        h0 = l0.distanceSquaredTo(destination);
        exists0 = rc.onTheMap(l0) && rc.sensePassability(l0) && !rc.canSenseRobotAtLocation(l0);
        cost0 = 1;

        l1 = getLocationFromIndex(1, start);
        dist1 = 1000000;
        h1 = l1.distanceSquaredTo(destination);
        exists1 = rc.onTheMap(l1) && rc.sensePassability(l1) && !rc.canSenseRobotAtLocation(l1);
        cost1 = 1;

        l2 = getLocationFromIndex(2, start);
        dist2 = 1000000;
        h2 = l2.distanceSquaredTo(destination);
        exists2 = rc.onTheMap(l2) && rc.sensePassability(l2) && !rc.canSenseRobotAtLocation(l2);
        cost2 = 1;

        l3 = getLocationFromIndex(3, start);
        dist3 = 1000000;
        h3 = l3.distanceSquaredTo(destination);
        exists3 = rc.onTheMap(l3) && rc.sensePassability(l3) && !rc.canSenseRobotAtLocation(l3);
        cost3 = 1;

        l4 = getLocationFromIndex(4, start);
        dist4 = 1000000;
        h4 = l4.distanceSquaredTo(destination);
        exists4 = rc.onTheMap(l4) && rc.sensePassability(l4) && !rc.canSenseRobotAtLocation(l4);
        cost4 = 1;

        l5 = getLocationFromIndex(5, start);
        dist5 = 1000000;
        h5 = l5.distanceSquaredTo(destination);
        exists5 = rc.onTheMap(l5) && rc.sensePassability(l5) && !rc.canSenseRobotAtLocation(l5);
        cost5 = 1;

        l6 = getLocationFromIndex(6, start);
        dist6 = 1000000;
        h6 = l6.distanceSquaredTo(destination);
        exists6 = rc.onTheMap(l6) && rc.sensePassability(l6) && !rc.canSenseRobotAtLocation(l6);
        cost6 = 1;

        l7 = getLocationFromIndex(7, start);
        dist7 = 1000000;
        h7 = l7.distanceSquaredTo(destination);
        exists7 = rc.onTheMap(l7) && rc.sensePassability(l7) && !rc.canSenseRobotAtLocation(l7);
        cost7 = 1;

        l8 = getLocationFromIndex(8, start);
        dist8 = 1000000;
        h8 = l8.distanceSquaredTo(destination);
        exists8 = rc.onTheMap(l8) && rc.sensePassability(l8) && !rc.canSenseRobotAtLocation(l8);
        cost8 = 1;

        l9 = getLocationFromIndex(9, start);
        dist9 = 1000000;
        h9 = l9.distanceSquaredTo(destination);
        exists9 = rc.onTheMap(l9) && rc.sensePassability(l9) && !rc.canSenseRobotAtLocation(l9);
        cost9 = 1;

        l10 = getLocationFromIndex(10, start);
        dist10 = 1000000;
        h10 = l10.distanceSquaredTo(destination);
        exists10 = rc.onTheMap(l10) && rc.sensePassability(l10) && !rc.canSenseRobotAtLocation(l10);
        cost10 = 1;

        l11 = getLocationFromIndex(11, start);
        dist11 = 1000000;
        h11 = l11.distanceSquaredTo(destination);
        exists11 = rc.onTheMap(l11) && rc.sensePassability(l11) && !rc.canSenseRobotAtLocation(l11);
        cost11 = 1;

        l12 = getLocationFromIndex(12, start);
        dist12 = 1000000;
        h12 = l12.distanceSquaredTo(destination);
        exists12 = rc.onTheMap(l12) && rc.sensePassability(l12) && !rc.canSenseRobotAtLocation(l12);
        cost12 = 1;

        l13 = getLocationFromIndex(13, start);
        dist13 = 1000000;
        h13 = l13.distanceSquaredTo(destination);
        exists13 = rc.onTheMap(l13) && rc.sensePassability(l13) && !rc.canSenseRobotAtLocation(l13);
        cost13 = 1;

        l14 = getLocationFromIndex(14, start);
        dist14 = 1000000;
        h14 = l14.distanceSquaredTo(destination);
        exists14 = rc.onTheMap(l14) && rc.sensePassability(l14) && !rc.canSenseRobotAtLocation(l14);
        cost14 = 1;

        l15 = getLocationFromIndex(15, start);
        dist15 = 1000000;
        h15 = l15.distanceSquaredTo(destination);
        exists15 = rc.onTheMap(l15) && rc.sensePassability(l15) && !rc.canSenseRobotAtLocation(l15);
        cost15 = 1;

        l16 = getLocationFromIndex(16, start);
        dist16 = 1000000;
        h16 = l16.distanceSquaredTo(destination);
        exists16 = rc.onTheMap(l16) && rc.sensePassability(l16) && !rc.canSenseRobotAtLocation(l16);
        cost16 = 1;

        l17 = getLocationFromIndex(17, start);
        dist17 = 1000000;
        h17 = l17.distanceSquaredTo(destination);
        exists17 = rc.onTheMap(l17) && rc.sensePassability(l17) && !rc.canSenseRobotAtLocation(l17);
        cost17 = 1;

        l18 = getLocationFromIndex(18, start);
        dist18 = 1000000;
        h18 = l18.distanceSquaredTo(destination);
        exists18 = rc.onTheMap(l18) && rc.sensePassability(l18) && !rc.canSenseRobotAtLocation(l18);
        cost18 = 1;

        l19 = getLocationFromIndex(19, start);
        dist19 = 1000000;
        h19 = l19.distanceSquaredTo(destination);
        exists19 = rc.onTheMap(l19) && rc.sensePassability(l19) && !rc.canSenseRobotAtLocation(l19);
        cost19 = 1;

        l20 = getLocationFromIndex(20, start);
        dist20 = 1000000;
        h20 = l20.distanceSquaredTo(destination);
        exists20 = rc.onTheMap(l20) && rc.sensePassability(l20) && !rc.canSenseRobotAtLocation(l20);
        cost20 = 1;

        l21 = getLocationFromIndex(21, start);
        dist21 = 1000000;
        h21 = l21.distanceSquaredTo(destination);
        exists21 = rc.onTheMap(l21) && rc.sensePassability(l21) && !rc.canSenseRobotAtLocation(l21);
        cost21 = 1;

        l22 = getLocationFromIndex(22, start);
        dist22 = 1000000;
        h22 = l22.distanceSquaredTo(destination);
        exists22 = rc.onTheMap(l22) && rc.sensePassability(l22) && !rc.canSenseRobotAtLocation(l22);
        cost22 = 1;

        l23 = getLocationFromIndex(23, start);
        dist23 = 1000000;
        h23 = l23.distanceSquaredTo(destination);
        exists23 = rc.onTheMap(l23) && rc.sensePassability(l23) && !rc.canSenseRobotAtLocation(l23);
        cost23 = 1;

        l24 = getLocationFromIndex(24, start);
        dist24 = 0;
        h24 = start.distanceSquaredTo(destination);
        exists24 = true;
        cost24 = 0;

        l25 = getLocationFromIndex(25, start);
        dist25 = 1000000;
        h25 = l25.distanceSquaredTo(destination);
        exists25 = rc.onTheMap(l25) && rc.sensePassability(l25) && !rc.canSenseRobotAtLocation(l25);
        cost25 = 1;

        l26 = getLocationFromIndex(26, start);
        dist26 = 1000000;
        h26 = l26.distanceSquaredTo(destination);
        exists26 = rc.onTheMap(l26) && rc.sensePassability(l26) && !rc.canSenseRobotAtLocation(l26);
        cost26 = 1;

        l27 = getLocationFromIndex(27, start);
        dist27 = 1000000;
        h27 = l27.distanceSquaredTo(destination);
        exists27 = rc.onTheMap(l27) && rc.sensePassability(l27) && !rc.canSenseRobotAtLocation(l27);
        cost27 = 1;

        l28 = getLocationFromIndex(28, start);
        dist28 = 1000000;
        h28 = l28.distanceSquaredTo(destination);
        exists28 = rc.onTheMap(l28) && rc.sensePassability(l28) && !rc.canSenseRobotAtLocation(l28);
        cost28 = 1;

        l29 = getLocationFromIndex(29, start);
        dist29 = 1000000;
        h29 = l29.distanceSquaredTo(destination);
        exists29 = rc.onTheMap(l29) && rc.sensePassability(l29) && !rc.canSenseRobotAtLocation(l29);
        cost29 = 1;

        l30 = getLocationFromIndex(30, start);
        dist30 = 1000000;
        h30 = l30.distanceSquaredTo(destination);
        exists30 = rc.onTheMap(l30) && rc.sensePassability(l30) && !rc.canSenseRobotAtLocation(l30);
        cost30 = 1;

        l31 = getLocationFromIndex(31, start);
        dist31 = 1000000;
        h31 = l31.distanceSquaredTo(destination);
        exists31 = rc.onTheMap(l31) && rc.sensePassability(l31) && !rc.canSenseRobotAtLocation(l31);
        cost31 = 1;

        l32 = getLocationFromIndex(32, start);
        dist32 = 1000000;
        h32 = l32.distanceSquaredTo(destination);
        exists32 = rc.onTheMap(l32) && rc.sensePassability(l32) && !rc.canSenseRobotAtLocation(l32);
        cost32 = 1;

        l33 = getLocationFromIndex(33, start);
        dist33 = 1000000;
        h33 = l33.distanceSquaredTo(destination);
        exists33 = rc.onTheMap(l33) && rc.sensePassability(l33) && !rc.canSenseRobotAtLocation(l33);
        cost33 = 1;

        l34 = getLocationFromIndex(34, start);
        dist34 = 1000000;
        h34 = l34.distanceSquaredTo(destination);
        exists34 = rc.onTheMap(l34) && rc.sensePassability(l34) && !rc.canSenseRobotAtLocation(l34);
        cost34 = 1;

        l35 = getLocationFromIndex(35, start);
        dist35 = 1000000;
        h35 = l35.distanceSquaredTo(destination);
        exists35 = rc.onTheMap(l35) && rc.sensePassability(l35) && !rc.canSenseRobotAtLocation(l35);
        cost35 = 1;

        l36 = getLocationFromIndex(36, start);
        dist36 = 1000000;
        h36 = l36.distanceSquaredTo(destination);
        exists36 = rc.onTheMap(l36) && rc.sensePassability(l36) && !rc.canSenseRobotAtLocation(l36);
        cost36 = 1;

        l37 = getLocationFromIndex(37, start);
        dist37 = 1000000;
        h37 = l37.distanceSquaredTo(destination);
        exists37 = rc.onTheMap(l37) && rc.sensePassability(l37) && !rc.canSenseRobotAtLocation(l37);
        cost37 = 1;

        l38 = getLocationFromIndex(38, start);
        dist38 = 1000000;
        h38 = l38.distanceSquaredTo(destination);
        exists38 = rc.onTheMap(l38) && rc.sensePassability(l38) && !rc.canSenseRobotAtLocation(l38);
        cost38 = 1;

        l39 = getLocationFromIndex(39, start);
        dist39 = 1000000;
        h39 = l39.distanceSquaredTo(destination);
        exists39 = rc.onTheMap(l39) && rc.sensePassability(l39) && !rc.canSenseRobotAtLocation(l39);
        cost39 = 1;

        l40 = getLocationFromIndex(40, start);
        dist40 = 1000000;
        h40 = l40.distanceSquaredTo(destination);
        exists40 = rc.onTheMap(l40) && rc.sensePassability(l40) && !rc.canSenseRobotAtLocation(l40);
        cost40 = 1;

        l41 = getLocationFromIndex(41, start);
        dist41 = 1000000;
        h41 = l41.distanceSquaredTo(destination);
        exists41 = rc.onTheMap(l41) && rc.sensePassability(l41) && !rc.canSenseRobotAtLocation(l41);
        cost41 = 1;

        l42 = getLocationFromIndex(42, start);
        dist42 = 1000000;
        h42 = l42.distanceSquaredTo(destination);
        exists42 = rc.onTheMap(l42) && rc.sensePassability(l42) && !rc.canSenseRobotAtLocation(l42);
        cost42 = 1;

        l43 = getLocationFromIndex(43, start);
        dist43 = 1000000;
        h43 = l43.distanceSquaredTo(destination);
        exists43 = rc.onTheMap(l43) && rc.sensePassability(l43) && !rc.canSenseRobotAtLocation(l43);
        cost43 = 1;

        l44 = getLocationFromIndex(44, start);
        dist44 = 1000000;
        h44 = l44.distanceSquaredTo(destination);
        exists44 = rc.onTheMap(l44) && rc.sensePassability(l44) && !rc.canSenseRobotAtLocation(l44);
        cost44 = 1;

        l45 = getLocationFromIndex(45, start);
        dist45 = 1000000;
        h45 = l45.distanceSquaredTo(destination);
        exists45 = rc.onTheMap(l45) && rc.sensePassability(l45) && !rc.canSenseRobotAtLocation(l45);
        cost45 = 1;

        l46 = getLocationFromIndex(46, start);
        dist46 = 1000000;
        h46 = l46.distanceSquaredTo(destination);
        exists46 = rc.onTheMap(l46) && rc.sensePassability(l46) && !rc.canSenseRobotAtLocation(l46);
        cost46 = 1;

        l47 = getLocationFromIndex(47, start);
        dist47 = 1000000;
        h47 = l47.distanceSquaredTo(destination);
        exists47 = rc.onTheMap(l47) && rc.sensePassability(l47) && !rc.canSenseRobotAtLocation(l47);
        cost47 = 1;

        l48 = getLocationFromIndex(48, start);
        dist48 = 1000000;
        h48 = l48.distanceSquaredTo(destination);
        exists48 = rc.onTheMap(l48) && rc.sensePassability(l48) && !rc.canSenseRobotAtLocation(l48);
        cost48 = 1;

        int closestDistance = Integer.MAX_VALUE;
        int closestIndex = -1;
        if (exists24) {
            if (exists16 && dist24 + cost16 < dist16) {
                dist16 = dist24 + cost16;
                lookup[indexToLocalY(16)][indexToLocalX(16)] = Direction.SOUTHEAST;
                if (h16 < closestDistance) {
                    closestDistance = h16;
                    closestIndex = 16;
                }
            }
            if (exists17 && dist24 + cost17 < dist17) {
                dist17 = dist24 + cost17;
                lookup[indexToLocalY(17)][indexToLocalX(17)] = Direction.SOUTH;
                if (h17 < closestDistance) {
                    closestDistance = h17;
                    closestIndex = 17;
                }
            }
            if (exists18 && dist24 + cost18 < dist18) {
                dist18 = dist24 + cost18;
                lookup[indexToLocalY(18)][indexToLocalX(18)] = Direction.SOUTHWEST;
                if (h18 < closestDistance) {
                    closestDistance = h18;
                    closestIndex = 18;
                }
            }
            if (exists23 && dist24 + cost23 < dist23) {
                dist23 = dist24 + cost23;
                lookup[indexToLocalY(23)][indexToLocalX(23)] = Direction.EAST;
                if (h23 < closestDistance) {
                    closestDistance = h23;
                    closestIndex = 23;
                }
            }
            if (exists25 && dist24 + cost25 < dist25) {
                dist25 = dist24 + cost25;
                lookup[indexToLocalY(25)][indexToLocalX(25)] = Direction.WEST;
                if (h25 < closestDistance) {
                    closestDistance = h25;
                    closestIndex = 25;
                }
            }
            if (exists30 && dist24 + cost30 < dist30) {
                dist30 = dist24 + cost30;
                lookup[indexToLocalY(30)][indexToLocalX(30)] = Direction.NORTHEAST;
                if (h30 < closestDistance) {
                    closestDistance = h30;
                    closestIndex = 30;
                }
            }
            if (exists31 && dist24 + cost31 < dist31) {
                dist31 = dist24 + cost31;
                lookup[indexToLocalY(31)][indexToLocalX(31)] = Direction.NORTH;
                if (h31 < closestDistance) {
                    closestDistance = h31;
                    closestIndex = 31;
                }
            }
            if (exists32 && dist24 + cost32 < dist32) {
                dist32 = dist24 + cost32;
                lookup[indexToLocalY(32)][indexToLocalX(32)] = Direction.NORTHWEST;
                if (h32 < closestDistance) {
                    closestDistance = h32;
                    closestIndex = 32;
                }
            }
        }
        if (exists16) {
            if (exists8 && dist16 + cost8 < dist8) {
                dist8 = dist16 + cost8;
                lookup[indexToLocalY(8)][indexToLocalX(8)] = Direction.SOUTHEAST;
                if (h8 < closestDistance) {
                    closestDistance = h8;
                    closestIndex = 8;
                }
            }
            if (exists9 && dist16 + cost9 < dist9) {
                dist9 = dist16 + cost9;
                lookup[indexToLocalY(9)][indexToLocalX(9)] = Direction.SOUTH;
                if (h9 < closestDistance) {
                    closestDistance = h9;
                    closestIndex = 9;
                }
            }
            if (exists10 && dist16 + cost10 < dist10) {
                dist10 = dist16 + cost10;
                lookup[indexToLocalY(10)][indexToLocalX(10)] = Direction.SOUTHWEST;
                if (h10 < closestDistance) {
                    closestDistance = h10;
                    closestIndex = 10;
                }
            }
            if (exists15 && dist16 + cost15 < dist15) {
                dist15 = dist16 + cost15;
                lookup[indexToLocalY(15)][indexToLocalX(15)] = Direction.EAST;
                if (h15 < closestDistance) {
                    closestDistance = h15;
                    closestIndex = 15;
                }
            }
            if (exists17 && dist16 + cost17 < dist17) {
                dist17 = dist16 + cost17;
                lookup[indexToLocalY(17)][indexToLocalX(17)] = Direction.WEST;
                if (h17 < closestDistance) {
                    closestDistance = h17;
                    closestIndex = 17;
                }
            }
            if (exists22 && dist16 + cost22 < dist22) {
                dist22 = dist16 + cost22;
                lookup[indexToLocalY(22)][indexToLocalX(22)] = Direction.NORTHEAST;
                if (h22 < closestDistance) {
                    closestDistance = h22;
                    closestIndex = 22;
                }
            }
            if (exists23 && dist16 + cost23 < dist23) {
                dist23 = dist16 + cost23;
                lookup[indexToLocalY(23)][indexToLocalX(23)] = Direction.NORTH;
                if (h23 < closestDistance) {
                    closestDistance = h23;
                    closestIndex = 23;
                }
            }
            if (exists24 && dist16 + cost24 < dist24) {
                dist24 = dist16 + cost24;
                lookup[indexToLocalY(24)][indexToLocalX(24)] = Direction.NORTHWEST;
                if (h24 < closestDistance) {
                    closestDistance = h24;
                    closestIndex = 24;
                }
            }
        }
        if (exists17) {
            if (exists9 && dist17 + cost9 < dist9) {
                dist9 = dist17 + cost9;
                lookup[indexToLocalY(9)][indexToLocalX(9)] = Direction.SOUTHEAST;
                if (h9 < closestDistance) {
                    closestDistance = h9;
                    closestIndex = 9;
                }
            }
            if (exists10 && dist17 + cost10 < dist10) {
                dist10 = dist17 + cost10;
                lookup[indexToLocalY(10)][indexToLocalX(10)] = Direction.SOUTH;
                if (h10 < closestDistance) {
                    closestDistance = h10;
                    closestIndex = 10;
                }
            }
            if (exists11 && dist17 + cost11 < dist11) {
                dist11 = dist17 + cost11;
                lookup[indexToLocalY(11)][indexToLocalX(11)] = Direction.SOUTHWEST;
                if (h11 < closestDistance) {
                    closestDistance = h11;
                    closestIndex = 11;
                }
            }
            if (exists16 && dist17 + cost16 < dist16) {
                dist16 = dist17 + cost16;
                lookup[indexToLocalY(16)][indexToLocalX(16)] = Direction.EAST;
                if (h16 < closestDistance) {
                    closestDistance = h16;
                    closestIndex = 16;
                }
            }
            if (exists18 && dist17 + cost18 < dist18) {
                dist18 = dist17 + cost18;
                lookup[indexToLocalY(18)][indexToLocalX(18)] = Direction.WEST;
                if (h18 < closestDistance) {
                    closestDistance = h18;
                    closestIndex = 18;
                }
            }
            if (exists23 && dist17 + cost23 < dist23) {
                dist23 = dist17 + cost23;
                lookup[indexToLocalY(23)][indexToLocalX(23)] = Direction.NORTHEAST;
                if (h23 < closestDistance) {
                    closestDistance = h23;
                    closestIndex = 23;
                }
            }
            if (exists24 && dist17 + cost24 < dist24) {
                dist24 = dist17 + cost24;
                lookup[indexToLocalY(24)][indexToLocalX(24)] = Direction.NORTH;
                if (h24 < closestDistance) {
                    closestDistance = h24;
                    closestIndex = 24;
                }
            }
            if (exists25 && dist17 + cost25 < dist25) {
                dist25 = dist17 + cost25;
                lookup[indexToLocalY(25)][indexToLocalX(25)] = Direction.NORTHWEST;
                if (h25 < closestDistance) {
                    closestDistance = h25;
                    closestIndex = 25;
                }
            }
        }
        if (exists18) {
            if (exists10 && dist18 + cost10 < dist10) {
                dist10 = dist18 + cost10;
                lookup[indexToLocalY(10)][indexToLocalX(10)] = Direction.SOUTHEAST;
                if (h10 < closestDistance) {
                    closestDistance = h10;
                    closestIndex = 10;
                }
            }
            if (exists11 && dist18 + cost11 < dist11) {
                dist11 = dist18 + cost11;
                lookup[indexToLocalY(11)][indexToLocalX(11)] = Direction.SOUTH;
                if (h11 < closestDistance) {
                    closestDistance = h11;
                    closestIndex = 11;
                }
            }
            if (exists12 && dist18 + cost12 < dist12) {
                dist12 = dist18 + cost12;
                lookup[indexToLocalY(12)][indexToLocalX(12)] = Direction.SOUTHWEST;
                if (h12 < closestDistance) {
                    closestDistance = h12;
                    closestIndex = 12;
                }
            }
            if (exists17 && dist18 + cost17 < dist17) {
                dist17 = dist18 + cost17;
                lookup[indexToLocalY(17)][indexToLocalX(17)] = Direction.EAST;
                if (h17 < closestDistance) {
                    closestDistance = h17;
                    closestIndex = 17;
                }
            }
            if (exists19 && dist18 + cost19 < dist19) {
                dist19 = dist18 + cost19;
                lookup[indexToLocalY(19)][indexToLocalX(19)] = Direction.WEST;
                if (h19 < closestDistance) {
                    closestDistance = h19;
                    closestIndex = 19;
                }
            }
            if (exists24 && dist18 + cost24 < dist24) {
                dist24 = dist18 + cost24;
                lookup[indexToLocalY(24)][indexToLocalX(24)] = Direction.NORTHEAST;
                if (h24 < closestDistance) {
                    closestDistance = h24;
                    closestIndex = 24;
                }
            }
            if (exists25 && dist18 + cost25 < dist25) {
                dist25 = dist18 + cost25;
                lookup[indexToLocalY(25)][indexToLocalX(25)] = Direction.NORTH;
                if (h25 < closestDistance) {
                    closestDistance = h25;
                    closestIndex = 25;
                }
            }
            if (exists26 && dist18 + cost26 < dist26) {
                dist26 = dist18 + cost26;
                lookup[indexToLocalY(26)][indexToLocalX(26)] = Direction.NORTHWEST;
                if (h26 < closestDistance) {
                    closestDistance = h26;
                    closestIndex = 26;
                }
            }
        }
        if (exists23) {
            if (exists15 && dist23 + cost15 < dist15) {
                dist15 = dist23 + cost15;
                lookup[indexToLocalY(15)][indexToLocalX(15)] = Direction.SOUTHEAST;
                if (h15 < closestDistance) {
                    closestDistance = h15;
                    closestIndex = 15;
                }
            }
            if (exists16 && dist23 + cost16 < dist16) {
                dist16 = dist23 + cost16;
                lookup[indexToLocalY(16)][indexToLocalX(16)] = Direction.SOUTH;
                if (h16 < closestDistance) {
                    closestDistance = h16;
                    closestIndex = 16;
                }
            }
            if (exists17 && dist23 + cost17 < dist17) {
                dist17 = dist23 + cost17;
                lookup[indexToLocalY(17)][indexToLocalX(17)] = Direction.SOUTHWEST;
                if (h17 < closestDistance) {
                    closestDistance = h17;
                    closestIndex = 17;
                }
            }
            if (exists22 && dist23 + cost22 < dist22) {
                dist22 = dist23 + cost22;
                lookup[indexToLocalY(22)][indexToLocalX(22)] = Direction.EAST;
                if (h22 < closestDistance) {
                    closestDistance = h22;
                    closestIndex = 22;
                }
            }
            if (exists24 && dist23 + cost24 < dist24) {
                dist24 = dist23 + cost24;
                lookup[indexToLocalY(24)][indexToLocalX(24)] = Direction.WEST;
                if (h24 < closestDistance) {
                    closestDistance = h24;
                    closestIndex = 24;
                }
            }
            if (exists29 && dist23 + cost29 < dist29) {
                dist29 = dist23 + cost29;
                lookup[indexToLocalY(29)][indexToLocalX(29)] = Direction.NORTHEAST;
                if (h29 < closestDistance) {
                    closestDistance = h29;
                    closestIndex = 29;
                }
            }
            if (exists30 && dist23 + cost30 < dist30) {
                dist30 = dist23 + cost30;
                lookup[indexToLocalY(30)][indexToLocalX(30)] = Direction.NORTH;
                if (h30 < closestDistance) {
                    closestDistance = h30;
                    closestIndex = 30;
                }
            }
            if (exists31 && dist23 + cost31 < dist31) {
                dist31 = dist23 + cost31;
                lookup[indexToLocalY(31)][indexToLocalX(31)] = Direction.NORTHWEST;
                if (h31 < closestDistance) {
                    closestDistance = h31;
                    closestIndex = 31;
                }
            }
        }
        if (exists25) {
            if (exists17 && dist25 + cost17 < dist17) {
                dist17 = dist25 + cost17;
                lookup[indexToLocalY(17)][indexToLocalX(17)] = Direction.SOUTHEAST;
                if (h17 < closestDistance) {
                    closestDistance = h17;
                    closestIndex = 17;
                }
            }
            if (exists18 && dist25 + cost18 < dist18) {
                dist18 = dist25 + cost18;
                lookup[indexToLocalY(18)][indexToLocalX(18)] = Direction.SOUTH;
                if (h18 < closestDistance) {
                    closestDistance = h18;
                    closestIndex = 18;
                }
            }
            if (exists19 && dist25 + cost19 < dist19) {
                dist19 = dist25 + cost19;
                lookup[indexToLocalY(19)][indexToLocalX(19)] = Direction.SOUTHWEST;
                if (h19 < closestDistance) {
                    closestDistance = h19;
                    closestIndex = 19;
                }
            }
            if (exists24 && dist25 + cost24 < dist24) {
                dist24 = dist25 + cost24;
                lookup[indexToLocalY(24)][indexToLocalX(24)] = Direction.EAST;
                if (h24 < closestDistance) {
                    closestDistance = h24;
                    closestIndex = 24;
                }
            }
            if (exists26 && dist25 + cost26 < dist26) {
                dist26 = dist25 + cost26;
                lookup[indexToLocalY(26)][indexToLocalX(26)] = Direction.WEST;
                if (h26 < closestDistance) {
                    closestDistance = h26;
                    closestIndex = 26;
                }
            }
            if (exists31 && dist25 + cost31 < dist31) {
                dist31 = dist25 + cost31;
                lookup[indexToLocalY(31)][indexToLocalX(31)] = Direction.NORTHEAST;
                if (h31 < closestDistance) {
                    closestDistance = h31;
                    closestIndex = 31;
                }
            }
            if (exists32 && dist25 + cost32 < dist32) {
                dist32 = dist25 + cost32;
                lookup[indexToLocalY(32)][indexToLocalX(32)] = Direction.NORTH;
                if (h32 < closestDistance) {
                    closestDistance = h32;
                    closestIndex = 32;
                }
            }
            if (exists33 && dist25 + cost33 < dist33) {
                dist33 = dist25 + cost33;
                lookup[indexToLocalY(33)][indexToLocalX(33)] = Direction.NORTHWEST;
                if (h33 < closestDistance) {
                    closestDistance = h33;
                    closestIndex = 33;
                }
            }
        }
        if (exists30) {
            if (exists22 && dist30 + cost22 < dist22) {
                dist22 = dist30 + cost22;
                lookup[indexToLocalY(22)][indexToLocalX(22)] = Direction.SOUTHEAST;
                if (h22 < closestDistance) {
                    closestDistance = h22;
                    closestIndex = 22;
                }
            }
            if (exists23 && dist30 + cost23 < dist23) {
                dist23 = dist30 + cost23;
                lookup[indexToLocalY(23)][indexToLocalX(23)] = Direction.SOUTH;
                if (h23 < closestDistance) {
                    closestDistance = h23;
                    closestIndex = 23;
                }
            }
            if (exists24 && dist30 + cost24 < dist24) {
                dist24 = dist30 + cost24;
                lookup[indexToLocalY(24)][indexToLocalX(24)] = Direction.SOUTHWEST;
                if (h24 < closestDistance) {
                    closestDistance = h24;
                    closestIndex = 24;
                }
            }
            if (exists29 && dist30 + cost29 < dist29) {
                dist29 = dist30 + cost29;
                lookup[indexToLocalY(29)][indexToLocalX(29)] = Direction.EAST;
                if (h29 < closestDistance) {
                    closestDistance = h29;
                    closestIndex = 29;
                }
            }
            if (exists31 && dist30 + cost31 < dist31) {
                dist31 = dist30 + cost31;
                lookup[indexToLocalY(31)][indexToLocalX(31)] = Direction.WEST;
                if (h31 < closestDistance) {
                    closestDistance = h31;
                    closestIndex = 31;
                }
            }
            if (exists36 && dist30 + cost36 < dist36) {
                dist36 = dist30 + cost36;
                lookup[indexToLocalY(36)][indexToLocalX(36)] = Direction.NORTHEAST;
                if (h36 < closestDistance) {
                    closestDistance = h36;
                    closestIndex = 36;
                }
            }
            if (exists37 && dist30 + cost37 < dist37) {
                dist37 = dist30 + cost37;
                lookup[indexToLocalY(37)][indexToLocalX(37)] = Direction.NORTH;
                if (h37 < closestDistance) {
                    closestDistance = h37;
                    closestIndex = 37;
                }
            }
            if (exists38 && dist30 + cost38 < dist38) {
                dist38 = dist30 + cost38;
                lookup[indexToLocalY(38)][indexToLocalX(38)] = Direction.NORTHWEST;
                if (h38 < closestDistance) {
                    closestDistance = h38;
                    closestIndex = 38;
                }
            }
        }
        if (exists31) {
            if (exists23 && dist31 + cost23 < dist23) {
                dist23 = dist31 + cost23;
                lookup[indexToLocalY(23)][indexToLocalX(23)] = Direction.SOUTHEAST;
                if (h23 < closestDistance) {
                    closestDistance = h23;
                    closestIndex = 23;
                }
            }
            if (exists24 && dist31 + cost24 < dist24) {
                dist24 = dist31 + cost24;
                lookup[indexToLocalY(24)][indexToLocalX(24)] = Direction.SOUTH;
                if (h24 < closestDistance) {
                    closestDistance = h24;
                    closestIndex = 24;
                }
            }
            if (exists25 && dist31 + cost25 < dist25) {
                dist25 = dist31 + cost25;
                lookup[indexToLocalY(25)][indexToLocalX(25)] = Direction.SOUTHWEST;
                if (h25 < closestDistance) {
                    closestDistance = h25;
                    closestIndex = 25;
                }
            }
            if (exists30 && dist31 + cost30 < dist30) {
                dist30 = dist31 + cost30;
                lookup[indexToLocalY(30)][indexToLocalX(30)] = Direction.EAST;
                if (h30 < closestDistance) {
                    closestDistance = h30;
                    closestIndex = 30;
                }
            }
            if (exists32 && dist31 + cost32 < dist32) {
                dist32 = dist31 + cost32;
                lookup[indexToLocalY(32)][indexToLocalX(32)] = Direction.WEST;
                if (h32 < closestDistance) {
                    closestDistance = h32;
                    closestIndex = 32;
                }
            }
            if (exists37 && dist31 + cost37 < dist37) {
                dist37 = dist31 + cost37;
                lookup[indexToLocalY(37)][indexToLocalX(37)] = Direction.NORTHEAST;
                if (h37 < closestDistance) {
                    closestDistance = h37;
                    closestIndex = 37;
                }
            }
            if (exists38 && dist31 + cost38 < dist38) {
                dist38 = dist31 + cost38;
                lookup[indexToLocalY(38)][indexToLocalX(38)] = Direction.NORTH;
                if (h38 < closestDistance) {
                    closestDistance = h38;
                    closestIndex = 38;
                }
            }
            if (exists39 && dist31 + cost39 < dist39) {
                dist39 = dist31 + cost39;
                lookup[indexToLocalY(39)][indexToLocalX(39)] = Direction.NORTHWEST;
                if (h39 < closestDistance) {
                    closestDistance = h39;
                    closestIndex = 39;
                }
            }
        }
        if (exists32) {
            if (exists24 && dist32 + cost24 < dist24) {
                dist24 = dist32 + cost24;
                lookup[indexToLocalY(24)][indexToLocalX(24)] = Direction.SOUTHEAST;
                if (h24 < closestDistance) {
                    closestDistance = h24;
                    closestIndex = 24;
                }
            }
            if (exists25 && dist32 + cost25 < dist25) {
                dist25 = dist32 + cost25;
                lookup[indexToLocalY(25)][indexToLocalX(25)] = Direction.SOUTH;
                if (h25 < closestDistance) {
                    closestDistance = h25;
                    closestIndex = 25;
                }
            }
            if (exists26 && dist32 + cost26 < dist26) {
                dist26 = dist32 + cost26;
                lookup[indexToLocalY(26)][indexToLocalX(26)] = Direction.SOUTHWEST;
                if (h26 < closestDistance) {
                    closestDistance = h26;
                    closestIndex = 26;
                }
            }
            if (exists31 && dist32 + cost31 < dist31) {
                dist31 = dist32 + cost31;
                lookup[indexToLocalY(31)][indexToLocalX(31)] = Direction.EAST;
                if (h31 < closestDistance) {
                    closestDistance = h31;
                    closestIndex = 31;
                }
            }
            if (exists33 && dist32 + cost33 < dist33) {
                dist33 = dist32 + cost33;
                lookup[indexToLocalY(33)][indexToLocalX(33)] = Direction.WEST;
                if (h33 < closestDistance) {
                    closestDistance = h33;
                    closestIndex = 33;
                }
            }
            if (exists38 && dist32 + cost38 < dist38) {
                dist38 = dist32 + cost38;
                lookup[indexToLocalY(38)][indexToLocalX(38)] = Direction.NORTHEAST;
                if (h38 < closestDistance) {
                    closestDistance = h38;
                    closestIndex = 38;
                }
            }
            if (exists39 && dist32 + cost39 < dist39) {
                dist39 = dist32 + cost39;
                lookup[indexToLocalY(39)][indexToLocalX(39)] = Direction.NORTH;
                if (h39 < closestDistance) {
                    closestDistance = h39;
                    closestIndex = 39;
                }
            }
            if (exists40 && dist32 + cost40 < dist40) {
                dist40 = dist32 + cost40;
                lookup[indexToLocalY(40)][indexToLocalX(40)] = Direction.NORTHWEST;
                if (h40 < closestDistance) {
                    closestDistance = h40;
                    closestIndex = 40;
                }
            }
        }
        if (exists8) {
            if (exists0 && dist8 + cost0 < dist0) {
                dist0 = dist8 + cost0;
                lookup[indexToLocalY(0)][indexToLocalX(0)] = Direction.SOUTHEAST;
                if (h0 < closestDistance) {
                    closestDistance = h0;
                    closestIndex = 0;
                }
            }
            if (exists1 && dist8 + cost1 < dist1) {
                dist1 = dist8 + cost1;
                lookup[indexToLocalY(1)][indexToLocalX(1)] = Direction.SOUTH;
                if (h1 < closestDistance) {
                    closestDistance = h1;
                    closestIndex = 1;
                }
            }
            if (exists2 && dist8 + cost2 < dist2) {
                dist2 = dist8 + cost2;
                lookup[indexToLocalY(2)][indexToLocalX(2)] = Direction.SOUTHWEST;
                if (h2 < closestDistance) {
                    closestDistance = h2;
                    closestIndex = 2;
                }
            }
            if (exists7 && dist8 + cost7 < dist7) {
                dist7 = dist8 + cost7;
                lookup[indexToLocalY(7)][indexToLocalX(7)] = Direction.EAST;
                if (h7 < closestDistance) {
                    closestDistance = h7;
                    closestIndex = 7;
                }
            }
            if (exists9 && dist8 + cost9 < dist9) {
                dist9 = dist8 + cost9;
                lookup[indexToLocalY(9)][indexToLocalX(9)] = Direction.WEST;
                if (h9 < closestDistance) {
                    closestDistance = h9;
                    closestIndex = 9;
                }
            }
            if (exists14 && dist8 + cost14 < dist14) {
                dist14 = dist8 + cost14;
                lookup[indexToLocalY(14)][indexToLocalX(14)] = Direction.NORTHEAST;
                if (h14 < closestDistance) {
                    closestDistance = h14;
                    closestIndex = 14;
                }
            }
            if (exists15 && dist8 + cost15 < dist15) {
                dist15 = dist8 + cost15;
                lookup[indexToLocalY(15)][indexToLocalX(15)] = Direction.NORTH;
                if (h15 < closestDistance) {
                    closestDistance = h15;
                    closestIndex = 15;
                }
            }
            if (exists16 && dist8 + cost16 < dist16) {
                dist16 = dist8 + cost16;
                lookup[indexToLocalY(16)][indexToLocalX(16)] = Direction.NORTHWEST;
                if (h16 < closestDistance) {
                    closestDistance = h16;
                    closestIndex = 16;
                }
            }
        }
        if (exists9) {
            if (exists1 && dist9 + cost1 < dist1) {
                dist1 = dist9 + cost1;
                lookup[indexToLocalY(1)][indexToLocalX(1)] = Direction.SOUTHEAST;
                if (h1 < closestDistance) {
                    closestDistance = h1;
                    closestIndex = 1;
                }
            }
            if (exists2 && dist9 + cost2 < dist2) {
                dist2 = dist9 + cost2;
                lookup[indexToLocalY(2)][indexToLocalX(2)] = Direction.SOUTH;
                if (h2 < closestDistance) {
                    closestDistance = h2;
                    closestIndex = 2;
                }
            }
            if (exists3 && dist9 + cost3 < dist3) {
                dist3 = dist9 + cost3;
                lookup[indexToLocalY(3)][indexToLocalX(3)] = Direction.SOUTHWEST;
                if (h3 < closestDistance) {
                    closestDistance = h3;
                    closestIndex = 3;
                }
            }
            if (exists8 && dist9 + cost8 < dist8) {
                dist8 = dist9 + cost8;
                lookup[indexToLocalY(8)][indexToLocalX(8)] = Direction.EAST;
                if (h8 < closestDistance) {
                    closestDistance = h8;
                    closestIndex = 8;
                }
            }
            if (exists10 && dist9 + cost10 < dist10) {
                dist10 = dist9 + cost10;
                lookup[indexToLocalY(10)][indexToLocalX(10)] = Direction.WEST;
                if (h10 < closestDistance) {
                    closestDistance = h10;
                    closestIndex = 10;
                }
            }
            if (exists15 && dist9 + cost15 < dist15) {
                dist15 = dist9 + cost15;
                lookup[indexToLocalY(15)][indexToLocalX(15)] = Direction.NORTHEAST;
                if (h15 < closestDistance) {
                    closestDistance = h15;
                    closestIndex = 15;
                }
            }
            if (exists16 && dist9 + cost16 < dist16) {
                dist16 = dist9 + cost16;
                lookup[indexToLocalY(16)][indexToLocalX(16)] = Direction.NORTH;
                if (h16 < closestDistance) {
                    closestDistance = h16;
                    closestIndex = 16;
                }
            }
            if (exists17 && dist9 + cost17 < dist17) {
                dist17 = dist9 + cost17;
                lookup[indexToLocalY(17)][indexToLocalX(17)] = Direction.NORTHWEST;
                if (h17 < closestDistance) {
                    closestDistance = h17;
                    closestIndex = 17;
                }
            }
        }
        if (exists10) {
            if (exists2 && dist10 + cost2 < dist2) {
                dist2 = dist10 + cost2;
                lookup[indexToLocalY(2)][indexToLocalX(2)] = Direction.SOUTHEAST;
                if (h2 < closestDistance) {
                    closestDistance = h2;
                    closestIndex = 2;
                }
            }
            if (exists3 && dist10 + cost3 < dist3) {
                dist3 = dist10 + cost3;
                lookup[indexToLocalY(3)][indexToLocalX(3)] = Direction.SOUTH;
                if (h3 < closestDistance) {
                    closestDistance = h3;
                    closestIndex = 3;
                }
            }
            if (exists4 && dist10 + cost4 < dist4) {
                dist4 = dist10 + cost4;
                lookup[indexToLocalY(4)][indexToLocalX(4)] = Direction.SOUTHWEST;
                if (h4 < closestDistance) {
                    closestDistance = h4;
                    closestIndex = 4;
                }
            }
            if (exists9 && dist10 + cost9 < dist9) {
                dist9 = dist10 + cost9;
                lookup[indexToLocalY(9)][indexToLocalX(9)] = Direction.EAST;
                if (h9 < closestDistance) {
                    closestDistance = h9;
                    closestIndex = 9;
                }
            }
            if (exists11 && dist10 + cost11 < dist11) {
                dist11 = dist10 + cost11;
                lookup[indexToLocalY(11)][indexToLocalX(11)] = Direction.WEST;
                if (h11 < closestDistance) {
                    closestDistance = h11;
                    closestIndex = 11;
                }
            }
            if (exists16 && dist10 + cost16 < dist16) {
                dist16 = dist10 + cost16;
                lookup[indexToLocalY(16)][indexToLocalX(16)] = Direction.NORTHEAST;
                if (h16 < closestDistance) {
                    closestDistance = h16;
                    closestIndex = 16;
                }
            }
            if (exists17 && dist10 + cost17 < dist17) {
                dist17 = dist10 + cost17;
                lookup[indexToLocalY(17)][indexToLocalX(17)] = Direction.NORTH;
                if (h17 < closestDistance) {
                    closestDistance = h17;
                    closestIndex = 17;
                }
            }
            if (exists18 && dist10 + cost18 < dist18) {
                dist18 = dist10 + cost18;
                lookup[indexToLocalY(18)][indexToLocalX(18)] = Direction.NORTHWEST;
                if (h18 < closestDistance) {
                    closestDistance = h18;
                    closestIndex = 18;
                }
            }
        }
        if (exists15) {
            if (exists7 && dist15 + cost7 < dist7) {
                dist7 = dist15 + cost7;
                lookup[indexToLocalY(7)][indexToLocalX(7)] = Direction.SOUTHEAST;
                if (h7 < closestDistance) {
                    closestDistance = h7;
                    closestIndex = 7;
                }
            }
            if (exists8 && dist15 + cost8 < dist8) {
                dist8 = dist15 + cost8;
                lookup[indexToLocalY(8)][indexToLocalX(8)] = Direction.SOUTH;
                if (h8 < closestDistance) {
                    closestDistance = h8;
                    closestIndex = 8;
                }
            }
            if (exists9 && dist15 + cost9 < dist9) {
                dist9 = dist15 + cost9;
                lookup[indexToLocalY(9)][indexToLocalX(9)] = Direction.SOUTHWEST;
                if (h9 < closestDistance) {
                    closestDistance = h9;
                    closestIndex = 9;
                }
            }
            if (exists14 && dist15 + cost14 < dist14) {
                dist14 = dist15 + cost14;
                lookup[indexToLocalY(14)][indexToLocalX(14)] = Direction.EAST;
                if (h14 < closestDistance) {
                    closestDistance = h14;
                    closestIndex = 14;
                }
            }
            if (exists16 && dist15 + cost16 < dist16) {
                dist16 = dist15 + cost16;
                lookup[indexToLocalY(16)][indexToLocalX(16)] = Direction.WEST;
                if (h16 < closestDistance) {
                    closestDistance = h16;
                    closestIndex = 16;
                }
            }
            if (exists21 && dist15 + cost21 < dist21) {
                dist21 = dist15 + cost21;
                lookup[indexToLocalY(21)][indexToLocalX(21)] = Direction.NORTHEAST;
                if (h21 < closestDistance) {
                    closestDistance = h21;
                    closestIndex = 21;
                }
            }
            if (exists22 && dist15 + cost22 < dist22) {
                dist22 = dist15 + cost22;
                lookup[indexToLocalY(22)][indexToLocalX(22)] = Direction.NORTH;
                if (h22 < closestDistance) {
                    closestDistance = h22;
                    closestIndex = 22;
                }
            }
            if (exists23 && dist15 + cost23 < dist23) {
                dist23 = dist15 + cost23;
                lookup[indexToLocalY(23)][indexToLocalX(23)] = Direction.NORTHWEST;
                if (h23 < closestDistance) {
                    closestDistance = h23;
                    closestIndex = 23;
                }
            }
        }
        if (exists22) {
            if (exists14 && dist22 + cost14 < dist14) {
                dist14 = dist22 + cost14;
                lookup[indexToLocalY(14)][indexToLocalX(14)] = Direction.SOUTHEAST;
                if (h14 < closestDistance) {
                    closestDistance = h14;
                    closestIndex = 14;
                }
            }
            if (exists15 && dist22 + cost15 < dist15) {
                dist15 = dist22 + cost15;
                lookup[indexToLocalY(15)][indexToLocalX(15)] = Direction.SOUTH;
                if (h15 < closestDistance) {
                    closestDistance = h15;
                    closestIndex = 15;
                }
            }
            if (exists16 && dist22 + cost16 < dist16) {
                dist16 = dist22 + cost16;
                lookup[indexToLocalY(16)][indexToLocalX(16)] = Direction.SOUTHWEST;
                if (h16 < closestDistance) {
                    closestDistance = h16;
                    closestIndex = 16;
                }
            }
            if (exists21 && dist22 + cost21 < dist21) {
                dist21 = dist22 + cost21;
                lookup[indexToLocalY(21)][indexToLocalX(21)] = Direction.EAST;
                if (h21 < closestDistance) {
                    closestDistance = h21;
                    closestIndex = 21;
                }
            }
            if (exists23 && dist22 + cost23 < dist23) {
                dist23 = dist22 + cost23;
                lookup[indexToLocalY(23)][indexToLocalX(23)] = Direction.WEST;
                if (h23 < closestDistance) {
                    closestDistance = h23;
                    closestIndex = 23;
                }
            }
            if (exists28 && dist22 + cost28 < dist28) {
                dist28 = dist22 + cost28;
                lookup[indexToLocalY(28)][indexToLocalX(28)] = Direction.NORTHEAST;
                if (h28 < closestDistance) {
                    closestDistance = h28;
                    closestIndex = 28;
                }
            }
            if (exists29 && dist22 + cost29 < dist29) {
                dist29 = dist22 + cost29;
                lookup[indexToLocalY(29)][indexToLocalX(29)] = Direction.NORTH;
                if (h29 < closestDistance) {
                    closestDistance = h29;
                    closestIndex = 29;
                }
            }
            if (exists30 && dist22 + cost30 < dist30) {
                dist30 = dist22 + cost30;
                lookup[indexToLocalY(30)][indexToLocalX(30)] = Direction.NORTHWEST;
                if (h30 < closestDistance) {
                    closestDistance = h30;
                    closestIndex = 30;
                }
            }
        }
        if (exists11) {
            if (exists3 && dist11 + cost3 < dist3) {
                dist3 = dist11 + cost3;
                lookup[indexToLocalY(3)][indexToLocalX(3)] = Direction.SOUTHEAST;
                if (h3 < closestDistance) {
                    closestDistance = h3;
                    closestIndex = 3;
                }
            }
            if (exists4 && dist11 + cost4 < dist4) {
                dist4 = dist11 + cost4;
                lookup[indexToLocalY(4)][indexToLocalX(4)] = Direction.SOUTH;
                if (h4 < closestDistance) {
                    closestDistance = h4;
                    closestIndex = 4;
                }
            }
            if (exists5 && dist11 + cost5 < dist5) {
                dist5 = dist11 + cost5;
                lookup[indexToLocalY(5)][indexToLocalX(5)] = Direction.SOUTHWEST;
                if (h5 < closestDistance) {
                    closestDistance = h5;
                    closestIndex = 5;
                }
            }
            if (exists10 && dist11 + cost10 < dist10) {
                dist10 = dist11 + cost10;
                lookup[indexToLocalY(10)][indexToLocalX(10)] = Direction.EAST;
                if (h10 < closestDistance) {
                    closestDistance = h10;
                    closestIndex = 10;
                }
            }
            if (exists12 && dist11 + cost12 < dist12) {
                dist12 = dist11 + cost12;
                lookup[indexToLocalY(12)][indexToLocalX(12)] = Direction.WEST;
                if (h12 < closestDistance) {
                    closestDistance = h12;
                    closestIndex = 12;
                }
            }
            if (exists17 && dist11 + cost17 < dist17) {
                dist17 = dist11 + cost17;
                lookup[indexToLocalY(17)][indexToLocalX(17)] = Direction.NORTHEAST;
                if (h17 < closestDistance) {
                    closestDistance = h17;
                    closestIndex = 17;
                }
            }
            if (exists18 && dist11 + cost18 < dist18) {
                dist18 = dist11 + cost18;
                lookup[indexToLocalY(18)][indexToLocalX(18)] = Direction.NORTH;
                if (h18 < closestDistance) {
                    closestDistance = h18;
                    closestIndex = 18;
                }
            }
            if (exists19 && dist11 + cost19 < dist19) {
                dist19 = dist11 + cost19;
                lookup[indexToLocalY(19)][indexToLocalX(19)] = Direction.NORTHWEST;
                if (h19 < closestDistance) {
                    closestDistance = h19;
                    closestIndex = 19;
                }
            }
        }
        if (exists12) {
            if (exists4 && dist12 + cost4 < dist4) {
                dist4 = dist12 + cost4;
                lookup[indexToLocalY(4)][indexToLocalX(4)] = Direction.SOUTHEAST;
                if (h4 < closestDistance) {
                    closestDistance = h4;
                    closestIndex = 4;
                }
            }
            if (exists5 && dist12 + cost5 < dist5) {
                dist5 = dist12 + cost5;
                lookup[indexToLocalY(5)][indexToLocalX(5)] = Direction.SOUTH;
                if (h5 < closestDistance) {
                    closestDistance = h5;
                    closestIndex = 5;
                }
            }
            if (exists6 && dist12 + cost6 < dist6) {
                dist6 = dist12 + cost6;
                lookup[indexToLocalY(6)][indexToLocalX(6)] = Direction.SOUTHWEST;
                if (h6 < closestDistance) {
                    closestDistance = h6;
                    closestIndex = 6;
                }
            }
            if (exists11 && dist12 + cost11 < dist11) {
                dist11 = dist12 + cost11;
                lookup[indexToLocalY(11)][indexToLocalX(11)] = Direction.EAST;
                if (h11 < closestDistance) {
                    closestDistance = h11;
                    closestIndex = 11;
                }
            }
            if (exists13 && dist12 + cost13 < dist13) {
                dist13 = dist12 + cost13;
                lookup[indexToLocalY(13)][indexToLocalX(13)] = Direction.WEST;
                if (h13 < closestDistance) {
                    closestDistance = h13;
                    closestIndex = 13;
                }
            }
            if (exists18 && dist12 + cost18 < dist18) {
                dist18 = dist12 + cost18;
                lookup[indexToLocalY(18)][indexToLocalX(18)] = Direction.NORTHEAST;
                if (h18 < closestDistance) {
                    closestDistance = h18;
                    closestIndex = 18;
                }
            }
            if (exists19 && dist12 + cost19 < dist19) {
                dist19 = dist12 + cost19;
                lookup[indexToLocalY(19)][indexToLocalX(19)] = Direction.NORTH;
                if (h19 < closestDistance) {
                    closestDistance = h19;
                    closestIndex = 19;
                }
            }
            if (exists20 && dist12 + cost20 < dist20) {
                dist20 = dist12 + cost20;
                lookup[indexToLocalY(20)][indexToLocalX(20)] = Direction.NORTHWEST;
                if (h20 < closestDistance) {
                    closestDistance = h20;
                    closestIndex = 20;
                }
            }
        }
        if (exists19) {
            if (exists11 && dist19 + cost11 < dist11) {
                dist11 = dist19 + cost11;
                lookup[indexToLocalY(11)][indexToLocalX(11)] = Direction.SOUTHEAST;
                if (h11 < closestDistance) {
                    closestDistance = h11;
                    closestIndex = 11;
                }
            }
            if (exists12 && dist19 + cost12 < dist12) {
                dist12 = dist19 + cost12;
                lookup[indexToLocalY(12)][indexToLocalX(12)] = Direction.SOUTH;
                if (h12 < closestDistance) {
                    closestDistance = h12;
                    closestIndex = 12;
                }
            }
            if (exists13 && dist19 + cost13 < dist13) {
                dist13 = dist19 + cost13;
                lookup[indexToLocalY(13)][indexToLocalX(13)] = Direction.SOUTHWEST;
                if (h13 < closestDistance) {
                    closestDistance = h13;
                    closestIndex = 13;
                }
            }
            if (exists18 && dist19 + cost18 < dist18) {
                dist18 = dist19 + cost18;
                lookup[indexToLocalY(18)][indexToLocalX(18)] = Direction.EAST;
                if (h18 < closestDistance) {
                    closestDistance = h18;
                    closestIndex = 18;
                }
            }
            if (exists20 && dist19 + cost20 < dist20) {
                dist20 = dist19 + cost20;
                lookup[indexToLocalY(20)][indexToLocalX(20)] = Direction.WEST;
                if (h20 < closestDistance) {
                    closestDistance = h20;
                    closestIndex = 20;
                }
            }
            if (exists25 && dist19 + cost25 < dist25) {
                dist25 = dist19 + cost25;
                lookup[indexToLocalY(25)][indexToLocalX(25)] = Direction.NORTHEAST;
                if (h25 < closestDistance) {
                    closestDistance = h25;
                    closestIndex = 25;
                }
            }
            if (exists26 && dist19 + cost26 < dist26) {
                dist26 = dist19 + cost26;
                lookup[indexToLocalY(26)][indexToLocalX(26)] = Direction.NORTH;
                if (h26 < closestDistance) {
                    closestDistance = h26;
                    closestIndex = 26;
                }
            }
            if (exists27 && dist19 + cost27 < dist27) {
                dist27 = dist19 + cost27;
                lookup[indexToLocalY(27)][indexToLocalX(27)] = Direction.NORTHWEST;
                if (h27 < closestDistance) {
                    closestDistance = h27;
                    closestIndex = 27;
                }
            }
        }
        if (exists26) {
            if (exists18 && dist26 + cost18 < dist18) {
                dist18 = dist26 + cost18;
                lookup[indexToLocalY(18)][indexToLocalX(18)] = Direction.SOUTHEAST;
                if (h18 < closestDistance) {
                    closestDistance = h18;
                    closestIndex = 18;
                }
            }
            if (exists19 && dist26 + cost19 < dist19) {
                dist19 = dist26 + cost19;
                lookup[indexToLocalY(19)][indexToLocalX(19)] = Direction.SOUTH;
                if (h19 < closestDistance) {
                    closestDistance = h19;
                    closestIndex = 19;
                }
            }
            if (exists20 && dist26 + cost20 < dist20) {
                dist20 = dist26 + cost20;
                lookup[indexToLocalY(20)][indexToLocalX(20)] = Direction.SOUTHWEST;
                if (h20 < closestDistance) {
                    closestDistance = h20;
                    closestIndex = 20;
                }
            }
            if (exists25 && dist26 + cost25 < dist25) {
                dist25 = dist26 + cost25;
                lookup[indexToLocalY(25)][indexToLocalX(25)] = Direction.EAST;
                if (h25 < closestDistance) {
                    closestDistance = h25;
                    closestIndex = 25;
                }
            }
            if (exists27 && dist26 + cost27 < dist27) {
                dist27 = dist26 + cost27;
                lookup[indexToLocalY(27)][indexToLocalX(27)] = Direction.WEST;
                if (h27 < closestDistance) {
                    closestDistance = h27;
                    closestIndex = 27;
                }
            }
            if (exists32 && dist26 + cost32 < dist32) {
                dist32 = dist26 + cost32;
                lookup[indexToLocalY(32)][indexToLocalX(32)] = Direction.NORTHEAST;
                if (h32 < closestDistance) {
                    closestDistance = h32;
                    closestIndex = 32;
                }
            }
            if (exists33 && dist26 + cost33 < dist33) {
                dist33 = dist26 + cost33;
                lookup[indexToLocalY(33)][indexToLocalX(33)] = Direction.NORTH;
                if (h33 < closestDistance) {
                    closestDistance = h33;
                    closestIndex = 33;
                }
            }
            if (exists34 && dist26 + cost34 < dist34) {
                dist34 = dist26 + cost34;
                lookup[indexToLocalY(34)][indexToLocalX(34)] = Direction.NORTHWEST;
                if (h34 < closestDistance) {
                    closestDistance = h34;
                    closestIndex = 34;
                }
            }
        }
        if (exists29) {
            if (exists21 && dist29 + cost21 < dist21) {
                dist21 = dist29 + cost21;
                lookup[indexToLocalY(21)][indexToLocalX(21)] = Direction.SOUTHEAST;
                if (h21 < closestDistance) {
                    closestDistance = h21;
                    closestIndex = 21;
                }
            }
            if (exists22 && dist29 + cost22 < dist22) {
                dist22 = dist29 + cost22;
                lookup[indexToLocalY(22)][indexToLocalX(22)] = Direction.SOUTH;
                if (h22 < closestDistance) {
                    closestDistance = h22;
                    closestIndex = 22;
                }
            }
            if (exists23 && dist29 + cost23 < dist23) {
                dist23 = dist29 + cost23;
                lookup[indexToLocalY(23)][indexToLocalX(23)] = Direction.SOUTHWEST;
                if (h23 < closestDistance) {
                    closestDistance = h23;
                    closestIndex = 23;
                }
            }
            if (exists28 && dist29 + cost28 < dist28) {
                dist28 = dist29 + cost28;
                lookup[indexToLocalY(28)][indexToLocalX(28)] = Direction.EAST;
                if (h28 < closestDistance) {
                    closestDistance = h28;
                    closestIndex = 28;
                }
            }
            if (exists30 && dist29 + cost30 < dist30) {
                dist30 = dist29 + cost30;
                lookup[indexToLocalY(30)][indexToLocalX(30)] = Direction.WEST;
                if (h30 < closestDistance) {
                    closestDistance = h30;
                    closestIndex = 30;
                }
            }
            if (exists35 && dist29 + cost35 < dist35) {
                dist35 = dist29 + cost35;
                lookup[indexToLocalY(35)][indexToLocalX(35)] = Direction.NORTHEAST;
                if (h35 < closestDistance) {
                    closestDistance = h35;
                    closestIndex = 35;
                }
            }
            if (exists36 && dist29 + cost36 < dist36) {
                dist36 = dist29 + cost36;
                lookup[indexToLocalY(36)][indexToLocalX(36)] = Direction.NORTH;
                if (h36 < closestDistance) {
                    closestDistance = h36;
                    closestIndex = 36;
                }
            }
            if (exists37 && dist29 + cost37 < dist37) {
                dist37 = dist29 + cost37;
                lookup[indexToLocalY(37)][indexToLocalX(37)] = Direction.NORTHWEST;
                if (h37 < closestDistance) {
                    closestDistance = h37;
                    closestIndex = 37;
                }
            }
        }
        if (exists33) {
            if (exists25 && dist33 + cost25 < dist25) {
                dist25 = dist33 + cost25;
                lookup[indexToLocalY(25)][indexToLocalX(25)] = Direction.SOUTHEAST;
                if (h25 < closestDistance) {
                    closestDistance = h25;
                    closestIndex = 25;
                }
            }
            if (exists26 && dist33 + cost26 < dist26) {
                dist26 = dist33 + cost26;
                lookup[indexToLocalY(26)][indexToLocalX(26)] = Direction.SOUTH;
                if (h26 < closestDistance) {
                    closestDistance = h26;
                    closestIndex = 26;
                }
            }
            if (exists27 && dist33 + cost27 < dist27) {
                dist27 = dist33 + cost27;
                lookup[indexToLocalY(27)][indexToLocalX(27)] = Direction.SOUTHWEST;
                if (h27 < closestDistance) {
                    closestDistance = h27;
                    closestIndex = 27;
                }
            }
            if (exists32 && dist33 + cost32 < dist32) {
                dist32 = dist33 + cost32;
                lookup[indexToLocalY(32)][indexToLocalX(32)] = Direction.EAST;
                if (h32 < closestDistance) {
                    closestDistance = h32;
                    closestIndex = 32;
                }
            }
            if (exists34 && dist33 + cost34 < dist34) {
                dist34 = dist33 + cost34;
                lookup[indexToLocalY(34)][indexToLocalX(34)] = Direction.WEST;
                if (h34 < closestDistance) {
                    closestDistance = h34;
                    closestIndex = 34;
                }
            }
            if (exists39 && dist33 + cost39 < dist39) {
                dist39 = dist33 + cost39;
                lookup[indexToLocalY(39)][indexToLocalX(39)] = Direction.NORTHEAST;
                if (h39 < closestDistance) {
                    closestDistance = h39;
                    closestIndex = 39;
                }
            }
            if (exists40 && dist33 + cost40 < dist40) {
                dist40 = dist33 + cost40;
                lookup[indexToLocalY(40)][indexToLocalX(40)] = Direction.NORTH;
                if (h40 < closestDistance) {
                    closestDistance = h40;
                    closestIndex = 40;
                }
            }
            if (exists41 && dist33 + cost41 < dist41) {
                dist41 = dist33 + cost41;
                lookup[indexToLocalY(41)][indexToLocalX(41)] = Direction.NORTHWEST;
                if (h41 < closestDistance) {
                    closestDistance = h41;
                    closestIndex = 41;
                }
            }
        }
        if (exists36) {
            if (exists28 && dist36 + cost28 < dist28) {
                dist28 = dist36 + cost28;
                lookup[indexToLocalY(28)][indexToLocalX(28)] = Direction.SOUTHEAST;
                if (h28 < closestDistance) {
                    closestDistance = h28;
                    closestIndex = 28;
                }
            }
            if (exists29 && dist36 + cost29 < dist29) {
                dist29 = dist36 + cost29;
                lookup[indexToLocalY(29)][indexToLocalX(29)] = Direction.SOUTH;
                if (h29 < closestDistance) {
                    closestDistance = h29;
                    closestIndex = 29;
                }
            }
            if (exists30 && dist36 + cost30 < dist30) {
                dist30 = dist36 + cost30;
                lookup[indexToLocalY(30)][indexToLocalX(30)] = Direction.SOUTHWEST;
                if (h30 < closestDistance) {
                    closestDistance = h30;
                    closestIndex = 30;
                }
            }
            if (exists35 && dist36 + cost35 < dist35) {
                dist35 = dist36 + cost35;
                lookup[indexToLocalY(35)][indexToLocalX(35)] = Direction.EAST;
                if (h35 < closestDistance) {
                    closestDistance = h35;
                    closestIndex = 35;
                }
            }
            if (exists37 && dist36 + cost37 < dist37) {
                dist37 = dist36 + cost37;
                lookup[indexToLocalY(37)][indexToLocalX(37)] = Direction.WEST;
                if (h37 < closestDistance) {
                    closestDistance = h37;
                    closestIndex = 37;
                }
            }
            if (exists42 && dist36 + cost42 < dist42) {
                dist42 = dist36 + cost42;
                lookup[indexToLocalY(42)][indexToLocalX(42)] = Direction.NORTHEAST;
                if (h42 < closestDistance) {
                    closestDistance = h42;
                    closestIndex = 42;
                }
            }
            if (exists43 && dist36 + cost43 < dist43) {
                dist43 = dist36 + cost43;
                lookup[indexToLocalY(43)][indexToLocalX(43)] = Direction.NORTH;
                if (h43 < closestDistance) {
                    closestDistance = h43;
                    closestIndex = 43;
                }
            }
            if (exists44 && dist36 + cost44 < dist44) {
                dist44 = dist36 + cost44;
                lookup[indexToLocalY(44)][indexToLocalX(44)] = Direction.NORTHWEST;
                if (h44 < closestDistance) {
                    closestDistance = h44;
                    closestIndex = 44;
                }
            }
        }
        if (exists37) {
            if (exists29 && dist37 + cost29 < dist29) {
                dist29 = dist37 + cost29;
                lookup[indexToLocalY(29)][indexToLocalX(29)] = Direction.SOUTHEAST;
                if (h29 < closestDistance) {
                    closestDistance = h29;
                    closestIndex = 29;
                }
            }
            if (exists30 && dist37 + cost30 < dist30) {
                dist30 = dist37 + cost30;
                lookup[indexToLocalY(30)][indexToLocalX(30)] = Direction.SOUTH;
                if (h30 < closestDistance) {
                    closestDistance = h30;
                    closestIndex = 30;
                }
            }
            if (exists31 && dist37 + cost31 < dist31) {
                dist31 = dist37 + cost31;
                lookup[indexToLocalY(31)][indexToLocalX(31)] = Direction.SOUTHWEST;
                if (h31 < closestDistance) {
                    closestDistance = h31;
                    closestIndex = 31;
                }
            }
            if (exists36 && dist37 + cost36 < dist36) {
                dist36 = dist37 + cost36;
                lookup[indexToLocalY(36)][indexToLocalX(36)] = Direction.EAST;
                if (h36 < closestDistance) {
                    closestDistance = h36;
                    closestIndex = 36;
                }
            }
            if (exists38 && dist37 + cost38 < dist38) {
                dist38 = dist37 + cost38;
                lookup[indexToLocalY(38)][indexToLocalX(38)] = Direction.WEST;
                if (h38 < closestDistance) {
                    closestDistance = h38;
                    closestIndex = 38;
                }
            }
            if (exists43 && dist37 + cost43 < dist43) {
                dist43 = dist37 + cost43;
                lookup[indexToLocalY(43)][indexToLocalX(43)] = Direction.NORTHEAST;
                if (h43 < closestDistance) {
                    closestDistance = h43;
                    closestIndex = 43;
                }
            }
            if (exists44 && dist37 + cost44 < dist44) {
                dist44 = dist37 + cost44;
                lookup[indexToLocalY(44)][indexToLocalX(44)] = Direction.NORTH;
                if (h44 < closestDistance) {
                    closestDistance = h44;
                    closestIndex = 44;
                }
            }
            if (exists45 && dist37 + cost45 < dist45) {
                dist45 = dist37 + cost45;
                lookup[indexToLocalY(45)][indexToLocalX(45)] = Direction.NORTHWEST;
                if (h45 < closestDistance) {
                    closestDistance = h45;
                    closestIndex = 45;
                }
            }
        }
        if (exists38) {
            if (exists30 && dist38 + cost30 < dist30) {
                dist30 = dist38 + cost30;
                lookup[indexToLocalY(30)][indexToLocalX(30)] = Direction.SOUTHEAST;
                if (h30 < closestDistance) {
                    closestDistance = h30;
                    closestIndex = 30;
                }
            }
            if (exists31 && dist38 + cost31 < dist31) {
                dist31 = dist38 + cost31;
                lookup[indexToLocalY(31)][indexToLocalX(31)] = Direction.SOUTH;
                if (h31 < closestDistance) {
                    closestDistance = h31;
                    closestIndex = 31;
                }
            }
            if (exists32 && dist38 + cost32 < dist32) {
                dist32 = dist38 + cost32;
                lookup[indexToLocalY(32)][indexToLocalX(32)] = Direction.SOUTHWEST;
                if (h32 < closestDistance) {
                    closestDistance = h32;
                    closestIndex = 32;
                }
            }
            if (exists37 && dist38 + cost37 < dist37) {
                dist37 = dist38 + cost37;
                lookup[indexToLocalY(37)][indexToLocalX(37)] = Direction.EAST;
                if (h37 < closestDistance) {
                    closestDistance = h37;
                    closestIndex = 37;
                }
            }
            if (exists39 && dist38 + cost39 < dist39) {
                dist39 = dist38 + cost39;
                lookup[indexToLocalY(39)][indexToLocalX(39)] = Direction.WEST;
                if (h39 < closestDistance) {
                    closestDistance = h39;
                    closestIndex = 39;
                }
            }
            if (exists44 && dist38 + cost44 < dist44) {
                dist44 = dist38 + cost44;
                lookup[indexToLocalY(44)][indexToLocalX(44)] = Direction.NORTHEAST;
                if (h44 < closestDistance) {
                    closestDistance = h44;
                    closestIndex = 44;
                }
            }
            if (exists45 && dist38 + cost45 < dist45) {
                dist45 = dist38 + cost45;
                lookup[indexToLocalY(45)][indexToLocalX(45)] = Direction.NORTH;
                if (h45 < closestDistance) {
                    closestDistance = h45;
                    closestIndex = 45;
                }
            }
            if (exists46 && dist38 + cost46 < dist46) {
                dist46 = dist38 + cost46;
                lookup[indexToLocalY(46)][indexToLocalX(46)] = Direction.NORTHWEST;
                if (h46 < closestDistance) {
                    closestDistance = h46;
                    closestIndex = 46;
                }
            }
        }
        if (exists39) {
            if (exists31 && dist39 + cost31 < dist31) {
                dist31 = dist39 + cost31;
                lookup[indexToLocalY(31)][indexToLocalX(31)] = Direction.SOUTHEAST;
                if (h31 < closestDistance) {
                    closestDistance = h31;
                    closestIndex = 31;
                }
            }
            if (exists32 && dist39 + cost32 < dist32) {
                dist32 = dist39 + cost32;
                lookup[indexToLocalY(32)][indexToLocalX(32)] = Direction.SOUTH;
                if (h32 < closestDistance) {
                    closestDistance = h32;
                    closestIndex = 32;
                }
            }
            if (exists33 && dist39 + cost33 < dist33) {
                dist33 = dist39 + cost33;
                lookup[indexToLocalY(33)][indexToLocalX(33)] = Direction.SOUTHWEST;
                if (h33 < closestDistance) {
                    closestDistance = h33;
                    closestIndex = 33;
                }
            }
            if (exists38 && dist39 + cost38 < dist38) {
                dist38 = dist39 + cost38;
                lookup[indexToLocalY(38)][indexToLocalX(38)] = Direction.EAST;
                if (h38 < closestDistance) {
                    closestDistance = h38;
                    closestIndex = 38;
                }
            }
            if (exists40 && dist39 + cost40 < dist40) {
                dist40 = dist39 + cost40;
                lookup[indexToLocalY(40)][indexToLocalX(40)] = Direction.WEST;
                if (h40 < closestDistance) {
                    closestDistance = h40;
                    closestIndex = 40;
                }
            }
            if (exists45 && dist39 + cost45 < dist45) {
                dist45 = dist39 + cost45;
                lookup[indexToLocalY(45)][indexToLocalX(45)] = Direction.NORTHEAST;
                if (h45 < closestDistance) {
                    closestDistance = h45;
                    closestIndex = 45;
                }
            }
            if (exists46 && dist39 + cost46 < dist46) {
                dist46 = dist39 + cost46;
                lookup[indexToLocalY(46)][indexToLocalX(46)] = Direction.NORTH;
                if (h46 < closestDistance) {
                    closestDistance = h46;
                    closestIndex = 46;
                }
            }
            if (exists47 && dist39 + cost47 < dist47) {
                dist47 = dist39 + cost47;
                lookup[indexToLocalY(47)][indexToLocalX(47)] = Direction.NORTHWEST;
                if (h47 < closestDistance) {
                    closestDistance = h47;
                    closestIndex = 47;
                }
            }
        }
        if (exists40) {
            if (exists32 && dist40 + cost32 < dist32) {
                dist32 = dist40 + cost32;
                lookup[indexToLocalY(32)][indexToLocalX(32)] = Direction.SOUTHEAST;
                if (h32 < closestDistance) {
                    closestDistance = h32;
                    closestIndex = 32;
                }
            }
            if (exists33 && dist40 + cost33 < dist33) {
                dist33 = dist40 + cost33;
                lookup[indexToLocalY(33)][indexToLocalX(33)] = Direction.SOUTH;
                if (h33 < closestDistance) {
                    closestDistance = h33;
                    closestIndex = 33;
                }
            }
            if (exists34 && dist40 + cost34 < dist34) {
                dist34 = dist40 + cost34;
                lookup[indexToLocalY(34)][indexToLocalX(34)] = Direction.SOUTHWEST;
                if (h34 < closestDistance) {
                    closestDistance = h34;
                    closestIndex = 34;
                }
            }
            if (exists39 && dist40 + cost39 < dist39) {
                dist39 = dist40 + cost39;
                lookup[indexToLocalY(39)][indexToLocalX(39)] = Direction.EAST;
                if (h39 < closestDistance) {
                    closestDistance = h39;
                    closestIndex = 39;
                }
            }
            if (exists41 && dist40 + cost41 < dist41) {
                dist41 = dist40 + cost41;
                lookup[indexToLocalY(41)][indexToLocalX(41)] = Direction.WEST;
                if (h41 < closestDistance) {
                    closestDistance = h41;
                    closestIndex = 41;
                }
            }
            if (exists46 && dist40 + cost46 < dist46) {
                dist46 = dist40 + cost46;
                lookup[indexToLocalY(46)][indexToLocalX(46)] = Direction.NORTHEAST;
                if (h46 < closestDistance) {
                    closestDistance = h46;
                    closestIndex = 46;
                }
            }
            if (exists47 && dist40 + cost47 < dist47) {
                dist47 = dist40 + cost47;
                lookup[indexToLocalY(47)][indexToLocalX(47)] = Direction.NORTH;
                if (h47 < closestDistance) {
                    closestDistance = h47;
                    closestIndex = 47;
                }
            }
            if (exists48 && dist40 + cost48 < dist48) {
                dist48 = dist40 + cost48;
                lookup[indexToLocalY(48)][indexToLocalX(48)] = Direction.NORTHWEST;
                if (h48 < closestDistance) {
                    closestDistance = h48;
                    closestIndex = 48;
                }
            }
        }
        if (exists0) {
            if (exists1 && dist0 + cost1 < dist1) {
                dist1 = dist0 + cost1;
                lookup[indexToLocalY(1)][indexToLocalX(1)] = Direction.WEST;
                if (h1 < closestDistance) {
                    closestDistance = h1;
                    closestIndex = 1;
                }
            }
            if (exists7 && dist0 + cost7 < dist7) {
                dist7 = dist0 + cost7;
                lookup[indexToLocalY(7)][indexToLocalX(7)] = Direction.NORTH;
                if (h7 < closestDistance) {
                    closestDistance = h7;
                    closestIndex = 7;
                }
            }
            if (exists8 && dist0 + cost8 < dist8) {
                dist8 = dist0 + cost8;
                lookup[indexToLocalY(8)][indexToLocalX(8)] = Direction.NORTHWEST;
                if (h8 < closestDistance) {
                    closestDistance = h8;
                    closestIndex = 8;
                }
            }
        }
        if (exists1) {
            if (exists0 && dist1 + cost0 < dist0) {
                dist0 = dist1 + cost0;
                lookup[indexToLocalY(0)][indexToLocalX(0)] = Direction.EAST;
                if (h0 < closestDistance) {
                    closestDistance = h0;
                    closestIndex = 0;
                }
            }
            if (exists2 && dist1 + cost2 < dist2) {
                dist2 = dist1 + cost2;
                lookup[indexToLocalY(2)][indexToLocalX(2)] = Direction.WEST;
                if (h2 < closestDistance) {
                    closestDistance = h2;
                    closestIndex = 2;
                }
            }
            if (exists7 && dist1 + cost7 < dist7) {
                dist7 = dist1 + cost7;
                lookup[indexToLocalY(7)][indexToLocalX(7)] = Direction.NORTHEAST;
                if (h7 < closestDistance) {
                    closestDistance = h7;
                    closestIndex = 7;
                }
            }
            if (exists8 && dist1 + cost8 < dist8) {
                dist8 = dist1 + cost8;
                lookup[indexToLocalY(8)][indexToLocalX(8)] = Direction.NORTH;
                if (h8 < closestDistance) {
                    closestDistance = h8;
                    closestIndex = 8;
                }
            }
            if (exists9 && dist1 + cost9 < dist9) {
                dist9 = dist1 + cost9;
                lookup[indexToLocalY(9)][indexToLocalX(9)] = Direction.NORTHWEST;
                if (h9 < closestDistance) {
                    closestDistance = h9;
                    closestIndex = 9;
                }
            }
        }
        if (exists2) {
            if (exists1 && dist2 + cost1 < dist1) {
                dist1 = dist2 + cost1;
                lookup[indexToLocalY(1)][indexToLocalX(1)] = Direction.EAST;
                if (h1 < closestDistance) {
                    closestDistance = h1;
                    closestIndex = 1;
                }
            }
            if (exists3 && dist2 + cost3 < dist3) {
                dist3 = dist2 + cost3;
                lookup[indexToLocalY(3)][indexToLocalX(3)] = Direction.WEST;
                if (h3 < closestDistance) {
                    closestDistance = h3;
                    closestIndex = 3;
                }
            }
            if (exists8 && dist2 + cost8 < dist8) {
                dist8 = dist2 + cost8;
                lookup[indexToLocalY(8)][indexToLocalX(8)] = Direction.NORTHEAST;
                if (h8 < closestDistance) {
                    closestDistance = h8;
                    closestIndex = 8;
                }
            }
            if (exists9 && dist2 + cost9 < dist9) {
                dist9 = dist2 + cost9;
                lookup[indexToLocalY(9)][indexToLocalX(9)] = Direction.NORTH;
                if (h9 < closestDistance) {
                    closestDistance = h9;
                    closestIndex = 9;
                }
            }
            if (exists10 && dist2 + cost10 < dist10) {
                dist10 = dist2 + cost10;
                lookup[indexToLocalY(10)][indexToLocalX(10)] = Direction.NORTHWEST;
                if (h10 < closestDistance) {
                    closestDistance = h10;
                    closestIndex = 10;
                }
            }
        }
        if (exists7) {
            if (exists0 && dist7 + cost0 < dist0) {
                dist0 = dist7 + cost0;
                lookup[indexToLocalY(0)][indexToLocalX(0)] = Direction.SOUTH;
                if (h0 < closestDistance) {
                    closestDistance = h0;
                    closestIndex = 0;
                }
            }
            if (exists1 && dist7 + cost1 < dist1) {
                dist1 = dist7 + cost1;
                lookup[indexToLocalY(1)][indexToLocalX(1)] = Direction.SOUTHWEST;
                if (h1 < closestDistance) {
                    closestDistance = h1;
                    closestIndex = 1;
                }
            }
            if (exists8 && dist7 + cost8 < dist8) {
                dist8 = dist7 + cost8;
                lookup[indexToLocalY(8)][indexToLocalX(8)] = Direction.WEST;
                if (h8 < closestDistance) {
                    closestDistance = h8;
                    closestIndex = 8;
                }
            }
            if (exists14 && dist7 + cost14 < dist14) {
                dist14 = dist7 + cost14;
                lookup[indexToLocalY(14)][indexToLocalX(14)] = Direction.NORTH;
                if (h14 < closestDistance) {
                    closestDistance = h14;
                    closestIndex = 14;
                }
            }
            if (exists15 && dist7 + cost15 < dist15) {
                dist15 = dist7 + cost15;
                lookup[indexToLocalY(15)][indexToLocalX(15)] = Direction.NORTHWEST;
                if (h15 < closestDistance) {
                    closestDistance = h15;
                    closestIndex = 15;
                }
            }
        }
        if (exists14) {
            if (exists7 && dist14 + cost7 < dist7) {
                dist7 = dist14 + cost7;
                lookup[indexToLocalY(7)][indexToLocalX(7)] = Direction.SOUTH;
                if (h7 < closestDistance) {
                    closestDistance = h7;
                    closestIndex = 7;
                }
            }
            if (exists8 && dist14 + cost8 < dist8) {
                dist8 = dist14 + cost8;
                lookup[indexToLocalY(8)][indexToLocalX(8)] = Direction.SOUTHWEST;
                if (h8 < closestDistance) {
                    closestDistance = h8;
                    closestIndex = 8;
                }
            }
            if (exists15 && dist14 + cost15 < dist15) {
                dist15 = dist14 + cost15;
                lookup[indexToLocalY(15)][indexToLocalX(15)] = Direction.WEST;
                if (h15 < closestDistance) {
                    closestDistance = h15;
                    closestIndex = 15;
                }
            }
            if (exists21 && dist14 + cost21 < dist21) {
                dist21 = dist14 + cost21;
                lookup[indexToLocalY(21)][indexToLocalX(21)] = Direction.NORTH;
                if (h21 < closestDistance) {
                    closestDistance = h21;
                    closestIndex = 21;
                }
            }
            if (exists22 && dist14 + cost22 < dist22) {
                dist22 = dist14 + cost22;
                lookup[indexToLocalY(22)][indexToLocalX(22)] = Direction.NORTHWEST;
                if (h22 < closestDistance) {
                    closestDistance = h22;
                    closestIndex = 22;
                }
            }
        }
        if (exists3) {
            if (exists2 && dist3 + cost2 < dist2) {
                dist2 = dist3 + cost2;
                lookup[indexToLocalY(2)][indexToLocalX(2)] = Direction.EAST;
                if (h2 < closestDistance) {
                    closestDistance = h2;
                    closestIndex = 2;
                }
            }
            if (exists4 && dist3 + cost4 < dist4) {
                dist4 = dist3 + cost4;
                lookup[indexToLocalY(4)][indexToLocalX(4)] = Direction.WEST;
                if (h4 < closestDistance) {
                    closestDistance = h4;
                    closestIndex = 4;
                }
            }
            if (exists9 && dist3 + cost9 < dist9) {
                dist9 = dist3 + cost9;
                lookup[indexToLocalY(9)][indexToLocalX(9)] = Direction.NORTHEAST;
                if (h9 < closestDistance) {
                    closestDistance = h9;
                    closestIndex = 9;
                }
            }
            if (exists10 && dist3 + cost10 < dist10) {
                dist10 = dist3 + cost10;
                lookup[indexToLocalY(10)][indexToLocalX(10)] = Direction.NORTH;
                if (h10 < closestDistance) {
                    closestDistance = h10;
                    closestIndex = 10;
                }
            }
            if (exists11 && dist3 + cost11 < dist11) {
                dist11 = dist3 + cost11;
                lookup[indexToLocalY(11)][indexToLocalX(11)] = Direction.NORTHWEST;
                if (h11 < closestDistance) {
                    closestDistance = h11;
                    closestIndex = 11;
                }
            }
        }
        if (exists4) {
            if (exists3 && dist4 + cost3 < dist3) {
                dist3 = dist4 + cost3;
                lookup[indexToLocalY(3)][indexToLocalX(3)] = Direction.EAST;
                if (h3 < closestDistance) {
                    closestDistance = h3;
                    closestIndex = 3;
                }
            }
            if (exists5 && dist4 + cost5 < dist5) {
                dist5 = dist4 + cost5;
                lookup[indexToLocalY(5)][indexToLocalX(5)] = Direction.WEST;
                if (h5 < closestDistance) {
                    closestDistance = h5;
                    closestIndex = 5;
                }
            }
            if (exists10 && dist4 + cost10 < dist10) {
                dist10 = dist4 + cost10;
                lookup[indexToLocalY(10)][indexToLocalX(10)] = Direction.NORTHEAST;
                if (h10 < closestDistance) {
                    closestDistance = h10;
                    closestIndex = 10;
                }
            }
            if (exists11 && dist4 + cost11 < dist11) {
                dist11 = dist4 + cost11;
                lookup[indexToLocalY(11)][indexToLocalX(11)] = Direction.NORTH;
                if (h11 < closestDistance) {
                    closestDistance = h11;
                    closestIndex = 11;
                }
            }
            if (exists12 && dist4 + cost12 < dist12) {
                dist12 = dist4 + cost12;
                lookup[indexToLocalY(12)][indexToLocalX(12)] = Direction.NORTHWEST;
                if (h12 < closestDistance) {
                    closestDistance = h12;
                    closestIndex = 12;
                }
            }
        }
        if (exists21) {
            if (exists14 && dist21 + cost14 < dist14) {
                dist14 = dist21 + cost14;
                lookup[indexToLocalY(14)][indexToLocalX(14)] = Direction.SOUTH;
                if (h14 < closestDistance) {
                    closestDistance = h14;
                    closestIndex = 14;
                }
            }
            if (exists15 && dist21 + cost15 < dist15) {
                dist15 = dist21 + cost15;
                lookup[indexToLocalY(15)][indexToLocalX(15)] = Direction.SOUTHWEST;
                if (h15 < closestDistance) {
                    closestDistance = h15;
                    closestIndex = 15;
                }
            }
            if (exists22 && dist21 + cost22 < dist22) {
                dist22 = dist21 + cost22;
                lookup[indexToLocalY(22)][indexToLocalX(22)] = Direction.WEST;
                if (h22 < closestDistance) {
                    closestDistance = h22;
                    closestIndex = 22;
                }
            }
            if (exists28 && dist21 + cost28 < dist28) {
                dist28 = dist21 + cost28;
                lookup[indexToLocalY(28)][indexToLocalX(28)] = Direction.NORTH;
                if (h28 < closestDistance) {
                    closestDistance = h28;
                    closestIndex = 28;
                }
            }
            if (exists29 && dist21 + cost29 < dist29) {
                dist29 = dist21 + cost29;
                lookup[indexToLocalY(29)][indexToLocalX(29)] = Direction.NORTHWEST;
                if (h29 < closestDistance) {
                    closestDistance = h29;
                    closestIndex = 29;
                }
            }
        }
        if (exists28) {
            if (exists21 && dist28 + cost21 < dist21) {
                dist21 = dist28 + cost21;
                lookup[indexToLocalY(21)][indexToLocalX(21)] = Direction.SOUTH;
                if (h21 < closestDistance) {
                    closestDistance = h21;
                    closestIndex = 21;
                }
            }
            if (exists22 && dist28 + cost22 < dist22) {
                dist22 = dist28 + cost22;
                lookup[indexToLocalY(22)][indexToLocalX(22)] = Direction.SOUTHWEST;
                if (h22 < closestDistance) {
                    closestDistance = h22;
                    closestIndex = 22;
                }
            }
            if (exists29 && dist28 + cost29 < dist29) {
                dist29 = dist28 + cost29;
                lookup[indexToLocalY(29)][indexToLocalX(29)] = Direction.WEST;
                if (h29 < closestDistance) {
                    closestDistance = h29;
                    closestIndex = 29;
                }
            }
            if (exists35 && dist28 + cost35 < dist35) {
                dist35 = dist28 + cost35;
                lookup[indexToLocalY(35)][indexToLocalX(35)] = Direction.NORTH;
                if (h35 < closestDistance) {
                    closestDistance = h35;
                    closestIndex = 35;
                }
            }
            if (exists36 && dist28 + cost36 < dist36) {
                dist36 = dist28 + cost36;
                lookup[indexToLocalY(36)][indexToLocalX(36)] = Direction.NORTHWEST;
                if (h36 < closestDistance) {
                    closestDistance = h36;
                    closestIndex = 36;
                }
            }
        }
        if (exists5) {
            if (exists4 && dist5 + cost4 < dist4) {
                dist4 = dist5 + cost4;
                lookup[indexToLocalY(4)][indexToLocalX(4)] = Direction.EAST;
                if (h4 < closestDistance) {
                    closestDistance = h4;
                    closestIndex = 4;
                }
            }
            if (exists6 && dist5 + cost6 < dist6) {
                dist6 = dist5 + cost6;
                lookup[indexToLocalY(6)][indexToLocalX(6)] = Direction.WEST;
                if (h6 < closestDistance) {
                    closestDistance = h6;
                    closestIndex = 6;
                }
            }
            if (exists11 && dist5 + cost11 < dist11) {
                dist11 = dist5 + cost11;
                lookup[indexToLocalY(11)][indexToLocalX(11)] = Direction.NORTHEAST;
                if (h11 < closestDistance) {
                    closestDistance = h11;
                    closestIndex = 11;
                }
            }
            if (exists12 && dist5 + cost12 < dist12) {
                dist12 = dist5 + cost12;
                lookup[indexToLocalY(12)][indexToLocalX(12)] = Direction.NORTH;
                if (h12 < closestDistance) {
                    closestDistance = h12;
                    closestIndex = 12;
                }
            }
            if (exists13 && dist5 + cost13 < dist13) {
                dist13 = dist5 + cost13;
                lookup[indexToLocalY(13)][indexToLocalX(13)] = Direction.NORTHWEST;
                if (h13 < closestDistance) {
                    closestDistance = h13;
                    closestIndex = 13;
                }
            }
        }
        if (exists6) {
            if (exists5 && dist6 + cost5 < dist5) {
                dist5 = dist6 + cost5;
                lookup[indexToLocalY(5)][indexToLocalX(5)] = Direction.EAST;
                if (h5 < closestDistance) {
                    closestDistance = h5;
                    closestIndex = 5;
                }
            }
            if (exists12 && dist6 + cost12 < dist12) {
                dist12 = dist6 + cost12;
                lookup[indexToLocalY(12)][indexToLocalX(12)] = Direction.NORTHEAST;
                if (h12 < closestDistance) {
                    closestDistance = h12;
                    closestIndex = 12;
                }
            }
            if (exists13 && dist6 + cost13 < dist13) {
                dist13 = dist6 + cost13;
                lookup[indexToLocalY(13)][indexToLocalX(13)] = Direction.NORTH;
                if (h13 < closestDistance) {
                    closestDistance = h13;
                    closestIndex = 13;
                }
            }
        }
        if (exists13) {
            if (exists5 && dist13 + cost5 < dist5) {
                dist5 = dist13 + cost5;
                lookup[indexToLocalY(5)][indexToLocalX(5)] = Direction.SOUTHEAST;
                if (h5 < closestDistance) {
                    closestDistance = h5;
                    closestIndex = 5;
                }
            }
            if (exists6 && dist13 + cost6 < dist6) {
                dist6 = dist13 + cost6;
                lookup[indexToLocalY(6)][indexToLocalX(6)] = Direction.SOUTH;
                if (h6 < closestDistance) {
                    closestDistance = h6;
                    closestIndex = 6;
                }
            }
            if (exists12 && dist13 + cost12 < dist12) {
                dist12 = dist13 + cost12;
                lookup[indexToLocalY(12)][indexToLocalX(12)] = Direction.EAST;
                if (h12 < closestDistance) {
                    closestDistance = h12;
                    closestIndex = 12;
                }
            }
            if (exists19 && dist13 + cost19 < dist19) {
                dist19 = dist13 + cost19;
                lookup[indexToLocalY(19)][indexToLocalX(19)] = Direction.NORTHEAST;
                if (h19 < closestDistance) {
                    closestDistance = h19;
                    closestIndex = 19;
                }
            }
            if (exists20 && dist13 + cost20 < dist20) {
                dist20 = dist13 + cost20;
                lookup[indexToLocalY(20)][indexToLocalX(20)] = Direction.NORTH;
                if (h20 < closestDistance) {
                    closestDistance = h20;
                    closestIndex = 20;
                }
            }
        }
        if (exists20) {
            if (exists12 && dist20 + cost12 < dist12) {
                dist12 = dist20 + cost12;
                lookup[indexToLocalY(12)][indexToLocalX(12)] = Direction.SOUTHEAST;
                if (h12 < closestDistance) {
                    closestDistance = h12;
                    closestIndex = 12;
                }
            }
            if (exists13 && dist20 + cost13 < dist13) {
                dist13 = dist20 + cost13;
                lookup[indexToLocalY(13)][indexToLocalX(13)] = Direction.SOUTH;
                if (h13 < closestDistance) {
                    closestDistance = h13;
                    closestIndex = 13;
                }
            }
            if (exists19 && dist20 + cost19 < dist19) {
                dist19 = dist20 + cost19;
                lookup[indexToLocalY(19)][indexToLocalX(19)] = Direction.EAST;
                if (h19 < closestDistance) {
                    closestDistance = h19;
                    closestIndex = 19;
                }
            }
            if (exists26 && dist20 + cost26 < dist26) {
                dist26 = dist20 + cost26;
                lookup[indexToLocalY(26)][indexToLocalX(26)] = Direction.NORTHEAST;
                if (h26 < closestDistance) {
                    closestDistance = h26;
                    closestIndex = 26;
                }
            }
            if (exists27 && dist20 + cost27 < dist27) {
                dist27 = dist20 + cost27;
                lookup[indexToLocalY(27)][indexToLocalX(27)] = Direction.NORTH;
                if (h27 < closestDistance) {
                    closestDistance = h27;
                    closestIndex = 27;
                }
            }
        }
        if (exists27) {
            if (exists19 && dist27 + cost19 < dist19) {
                dist19 = dist27 + cost19;
                lookup[indexToLocalY(19)][indexToLocalX(19)] = Direction.SOUTHEAST;
                if (h19 < closestDistance) {
                    closestDistance = h19;
                    closestIndex = 19;
                }
            }
            if (exists20 && dist27 + cost20 < dist20) {
                dist20 = dist27 + cost20;
                lookup[indexToLocalY(20)][indexToLocalX(20)] = Direction.SOUTH;
                if (h20 < closestDistance) {
                    closestDistance = h20;
                    closestIndex = 20;
                }
            }
            if (exists26 && dist27 + cost26 < dist26) {
                dist26 = dist27 + cost26;
                lookup[indexToLocalY(26)][indexToLocalX(26)] = Direction.EAST;
                if (h26 < closestDistance) {
                    closestDistance = h26;
                    closestIndex = 26;
                }
            }
            if (exists33 && dist27 + cost33 < dist33) {
                dist33 = dist27 + cost33;
                lookup[indexToLocalY(33)][indexToLocalX(33)] = Direction.NORTHEAST;
                if (h33 < closestDistance) {
                    closestDistance = h33;
                    closestIndex = 33;
                }
            }
            if (exists34 && dist27 + cost34 < dist34) {
                dist34 = dist27 + cost34;
                lookup[indexToLocalY(34)][indexToLocalX(34)] = Direction.NORTH;
                if (h34 < closestDistance) {
                    closestDistance = h34;
                    closestIndex = 34;
                }
            }
        }
        if (exists34) {
            if (exists26 && dist34 + cost26 < dist26) {
                dist26 = dist34 + cost26;
                lookup[indexToLocalY(26)][indexToLocalX(26)] = Direction.SOUTHEAST;
                if (h26 < closestDistance) {
                    closestDistance = h26;
                    closestIndex = 26;
                }
            }
            if (exists27 && dist34 + cost27 < dist27) {
                dist27 = dist34 + cost27;
                lookup[indexToLocalY(27)][indexToLocalX(27)] = Direction.SOUTH;
                if (h27 < closestDistance) {
                    closestDistance = h27;
                    closestIndex = 27;
                }
            }
            if (exists33 && dist34 + cost33 < dist33) {
                dist33 = dist34 + cost33;
                lookup[indexToLocalY(33)][indexToLocalX(33)] = Direction.EAST;
                if (h33 < closestDistance) {
                    closestDistance = h33;
                    closestIndex = 33;
                }
            }
            if (exists40 && dist34 + cost40 < dist40) {
                dist40 = dist34 + cost40;
                lookup[indexToLocalY(40)][indexToLocalX(40)] = Direction.NORTHEAST;
                if (h40 < closestDistance) {
                    closestDistance = h40;
                    closestIndex = 40;
                }
            }
            if (exists41 && dist34 + cost41 < dist41) {
                dist41 = dist34 + cost41;
                lookup[indexToLocalY(41)][indexToLocalX(41)] = Direction.NORTH;
                if (h41 < closestDistance) {
                    closestDistance = h41;
                    closestIndex = 41;
                }
            }
        }
        if (exists35) {
            if (exists28 && dist35 + cost28 < dist28) {
                dist28 = dist35 + cost28;
                lookup[indexToLocalY(28)][indexToLocalX(28)] = Direction.SOUTH;
                if (h28 < closestDistance) {
                    closestDistance = h28;
                    closestIndex = 28;
                }
            }
            if (exists29 && dist35 + cost29 < dist29) {
                dist29 = dist35 + cost29;
                lookup[indexToLocalY(29)][indexToLocalX(29)] = Direction.SOUTHWEST;
                if (h29 < closestDistance) {
                    closestDistance = h29;
                    closestIndex = 29;
                }
            }
            if (exists36 && dist35 + cost36 < dist36) {
                dist36 = dist35 + cost36;
                lookup[indexToLocalY(36)][indexToLocalX(36)] = Direction.WEST;
                if (h36 < closestDistance) {
                    closestDistance = h36;
                    closestIndex = 36;
                }
            }
            if (exists42 && dist35 + cost42 < dist42) {
                dist42 = dist35 + cost42;
                lookup[indexToLocalY(42)][indexToLocalX(42)] = Direction.NORTH;
                if (h42 < closestDistance) {
                    closestDistance = h42;
                    closestIndex = 42;
                }
            }
            if (exists43 && dist35 + cost43 < dist43) {
                dist43 = dist35 + cost43;
                lookup[indexToLocalY(43)][indexToLocalX(43)] = Direction.NORTHWEST;
                if (h43 < closestDistance) {
                    closestDistance = h43;
                    closestIndex = 43;
                }
            }
        }
        if (exists41) {
            if (exists33 && dist41 + cost33 < dist33) {
                dist33 = dist41 + cost33;
                lookup[indexToLocalY(33)][indexToLocalX(33)] = Direction.SOUTHEAST;
                if (h33 < closestDistance) {
                    closestDistance = h33;
                    closestIndex = 33;
                }
            }
            if (exists34 && dist41 + cost34 < dist34) {
                dist34 = dist41 + cost34;
                lookup[indexToLocalY(34)][indexToLocalX(34)] = Direction.SOUTH;
                if (h34 < closestDistance) {
                    closestDistance = h34;
                    closestIndex = 34;
                }
            }
            if (exists40 && dist41 + cost40 < dist40) {
                dist40 = dist41 + cost40;
                lookup[indexToLocalY(40)][indexToLocalX(40)] = Direction.EAST;
                if (h40 < closestDistance) {
                    closestDistance = h40;
                    closestIndex = 40;
                }
            }
            if (exists47 && dist41 + cost47 < dist47) {
                dist47 = dist41 + cost47;
                lookup[indexToLocalY(47)][indexToLocalX(47)] = Direction.NORTHEAST;
                if (h47 < closestDistance) {
                    closestDistance = h47;
                    closestIndex = 47;
                }
            }
            if (exists48 && dist41 + cost48 < dist48) {
                dist48 = dist41 + cost48;
                lookup[indexToLocalY(48)][indexToLocalX(48)] = Direction.NORTH;
                if (h48 < closestDistance) {
                    closestDistance = h48;
                    closestIndex = 48;
                }
            }
        }
        if (exists42) {
            if (exists35 && dist42 + cost35 < dist35) {
                dist35 = dist42 + cost35;
                lookup[indexToLocalY(35)][indexToLocalX(35)] = Direction.SOUTH;
                if (h35 < closestDistance) {
                    closestDistance = h35;
                    closestIndex = 35;
                }
            }
            if (exists36 && dist42 + cost36 < dist36) {
                dist36 = dist42 + cost36;
                lookup[indexToLocalY(36)][indexToLocalX(36)] = Direction.SOUTHWEST;
                if (h36 < closestDistance) {
                    closestDistance = h36;
                    closestIndex = 36;
                }
            }
            if (exists43 && dist42 + cost43 < dist43) {
                dist43 = dist42 + cost43;
                lookup[indexToLocalY(43)][indexToLocalX(43)] = Direction.WEST;
                if (h43 < closestDistance) {
                    closestDistance = h43;
                    closestIndex = 43;
                }
            }
        }
        if (exists43) {
            if (exists35 && dist43 + cost35 < dist35) {
                dist35 = dist43 + cost35;
                lookup[indexToLocalY(35)][indexToLocalX(35)] = Direction.SOUTHEAST;
                if (h35 < closestDistance) {
                    closestDistance = h35;
                    closestIndex = 35;
                }
            }
            if (exists36 && dist43 + cost36 < dist36) {
                dist36 = dist43 + cost36;
                lookup[indexToLocalY(36)][indexToLocalX(36)] = Direction.SOUTH;
                if (h36 < closestDistance) {
                    closestDistance = h36;
                    closestIndex = 36;
                }
            }
            if (exists37 && dist43 + cost37 < dist37) {
                dist37 = dist43 + cost37;
                lookup[indexToLocalY(37)][indexToLocalX(37)] = Direction.SOUTHWEST;
                if (h37 < closestDistance) {
                    closestDistance = h37;
                    closestIndex = 37;
                }
            }
            if (exists42 && dist43 + cost42 < dist42) {
                dist42 = dist43 + cost42;
                lookup[indexToLocalY(42)][indexToLocalX(42)] = Direction.EAST;
                if (h42 < closestDistance) {
                    closestDistance = h42;
                    closestIndex = 42;
                }
            }
            if (exists44 && dist43 + cost44 < dist44) {
                dist44 = dist43 + cost44;
                lookup[indexToLocalY(44)][indexToLocalX(44)] = Direction.WEST;
                if (h44 < closestDistance) {
                    closestDistance = h44;
                    closestIndex = 44;
                }
            }
        }
        if (exists44) {
            if (exists36 && dist44 + cost36 < dist36) {
                dist36 = dist44 + cost36;
                lookup[indexToLocalY(36)][indexToLocalX(36)] = Direction.SOUTHEAST;
                if (h36 < closestDistance) {
                    closestDistance = h36;
                    closestIndex = 36;
                }
            }
            if (exists37 && dist44 + cost37 < dist37) {
                dist37 = dist44 + cost37;
                lookup[indexToLocalY(37)][indexToLocalX(37)] = Direction.SOUTH;
                if (h37 < closestDistance) {
                    closestDistance = h37;
                    closestIndex = 37;
                }
            }
            if (exists38 && dist44 + cost38 < dist38) {
                dist38 = dist44 + cost38;
                lookup[indexToLocalY(38)][indexToLocalX(38)] = Direction.SOUTHWEST;
                if (h38 < closestDistance) {
                    closestDistance = h38;
                    closestIndex = 38;
                }
            }
            if (exists43 && dist44 + cost43 < dist43) {
                dist43 = dist44 + cost43;
                lookup[indexToLocalY(43)][indexToLocalX(43)] = Direction.EAST;
                if (h43 < closestDistance) {
                    closestDistance = h43;
                    closestIndex = 43;
                }
            }
            if (exists45 && dist44 + cost45 < dist45) {
                dist45 = dist44 + cost45;
                lookup[indexToLocalY(45)][indexToLocalX(45)] = Direction.WEST;
                if (h45 < closestDistance) {
                    closestDistance = h45;
                    closestIndex = 45;
                }
            }
        }
        if (exists45) {
            if (exists37 && dist45 + cost37 < dist37) {
                dist37 = dist45 + cost37;
                lookup[indexToLocalY(37)][indexToLocalX(37)] = Direction.SOUTHEAST;
                if (h37 < closestDistance) {
                    closestDistance = h37;
                    closestIndex = 37;
                }
            }
            if (exists38 && dist45 + cost38 < dist38) {
                dist38 = dist45 + cost38;
                lookup[indexToLocalY(38)][indexToLocalX(38)] = Direction.SOUTH;
                if (h38 < closestDistance) {
                    closestDistance = h38;
                    closestIndex = 38;
                }
            }
            if (exists39 && dist45 + cost39 < dist39) {
                dist39 = dist45 + cost39;
                lookup[indexToLocalY(39)][indexToLocalX(39)] = Direction.SOUTHWEST;
                if (h39 < closestDistance) {
                    closestDistance = h39;
                    closestIndex = 39;
                }
            }
            if (exists44 && dist45 + cost44 < dist44) {
                dist44 = dist45 + cost44;
                lookup[indexToLocalY(44)][indexToLocalX(44)] = Direction.EAST;
                if (h44 < closestDistance) {
                    closestDistance = h44;
                    closestIndex = 44;
                }
            }
            if (exists46 && dist45 + cost46 < dist46) {
                dist46 = dist45 + cost46;
                lookup[indexToLocalY(46)][indexToLocalX(46)] = Direction.WEST;
                if (h46 < closestDistance) {
                    closestDistance = h46;
                    closestIndex = 46;
                }
            }
        }
        if (exists46) {
            if (exists38 && dist46 + cost38 < dist38) {
                dist38 = dist46 + cost38;
                lookup[indexToLocalY(38)][indexToLocalX(38)] = Direction.SOUTHEAST;
                if (h38 < closestDistance) {
                    closestDistance = h38;
                    closestIndex = 38;
                }
            }
            if (exists39 && dist46 + cost39 < dist39) {
                dist39 = dist46 + cost39;
                lookup[indexToLocalY(39)][indexToLocalX(39)] = Direction.SOUTH;
                if (h39 < closestDistance) {
                    closestDistance = h39;
                    closestIndex = 39;
                }
            }
            if (exists40 && dist46 + cost40 < dist40) {
                dist40 = dist46 + cost40;
                lookup[indexToLocalY(40)][indexToLocalX(40)] = Direction.SOUTHWEST;
                if (h40 < closestDistance) {
                    closestDistance = h40;
                    closestIndex = 40;
                }
            }
            if (exists45 && dist46 + cost45 < dist45) {
                dist45 = dist46 + cost45;
                lookup[indexToLocalY(45)][indexToLocalX(45)] = Direction.EAST;
                if (h45 < closestDistance) {
                    closestDistance = h45;
                    closestIndex = 45;
                }
            }
            if (exists47 && dist46 + cost47 < dist47) {
                dist47 = dist46 + cost47;
                lookup[indexToLocalY(47)][indexToLocalX(47)] = Direction.WEST;
                if (h47 < closestDistance) {
                    closestDistance = h47;
                    closestIndex = 47;
                }
            }
        }
        if (exists47) {
            if (exists39 && dist47 + cost39 < dist39) {
                dist39 = dist47 + cost39;
                lookup[indexToLocalY(39)][indexToLocalX(39)] = Direction.SOUTHEAST;
                if (h39 < closestDistance) {
                    closestDistance = h39;
                    closestIndex = 39;
                }
            }
            if (exists40 && dist47 + cost40 < dist40) {
                dist40 = dist47 + cost40;
                lookup[indexToLocalY(40)][indexToLocalX(40)] = Direction.SOUTH;
                if (h40 < closestDistance) {
                    closestDistance = h40;
                    closestIndex = 40;
                }
            }
            if (exists41 && dist47 + cost41 < dist41) {
                dist41 = dist47 + cost41;
                lookup[indexToLocalY(41)][indexToLocalX(41)] = Direction.SOUTHWEST;
                if (h41 < closestDistance) {
                    closestDistance = h41;
                    closestIndex = 41;
                }
            }
            if (exists46 && dist47 + cost46 < dist46) {
                dist46 = dist47 + cost46;
                lookup[indexToLocalY(46)][indexToLocalX(46)] = Direction.EAST;
                if (h46 < closestDistance) {
                    closestDistance = h46;
                    closestIndex = 46;
                }
            }
            if (exists48 && dist47 + cost48 < dist48) {
                dist48 = dist47 + cost48;
                lookup[indexToLocalY(48)][indexToLocalX(48)] = Direction.WEST;
                if (h48 < closestDistance) {
                    closestDistance = h48;
                    closestIndex = 48;
                }
            }
        }
        if (exists48) {
            if (exists40 && dist48 + cost40 < dist40) {
                dist40 = dist48 + cost40;
                lookup[indexToLocalY(40)][indexToLocalX(40)] = Direction.SOUTHEAST;
                if (h40 < closestDistance) {
                    closestDistance = h40;
                    closestIndex = 40;
                }
            }
            if (exists41 && dist48 + cost41 < dist41) {
                dist41 = dist48 + cost41;
                lookup[indexToLocalY(41)][indexToLocalX(41)] = Direction.SOUTH;
                if (h41 < closestDistance) {
                    closestDistance = h41;
                    closestIndex = 41;
                }
            }
            if (exists47 && dist48 + cost47 < dist47) {
                dist47 = dist48 + cost47;
                lookup[indexToLocalY(47)][indexToLocalX(47)] = Direction.EAST;
                if (h47 < closestDistance) {
                    closestDistance = h47;
                    closestIndex = 47;
                }
            }
        }

        if (closestDistance >= Pathfinding.closestDistanceOnPath) {
            return null;
        }
        Pathfinding.closestDistanceOnPath = closestDistance;
        return closestIndex != -1 ? dirToMove(closestIndex) : Direction.CENTER;
    }
}

package bench_spaark.fast;

import battlecode.common.*;

//Supports:
// insert RobotInfo
// clearing all RobotInfo
// iterating through all RobotInfo
// looking up RobotInfo by MapLocation or ID
// if there are duplicate robot ID's or MapLocations (e.g. due to incorrect information from comms),
// overwrites the previous data
public class RobotInfoMap {
    public static boolean toIdMod = true; // if "hashing" robot id in comms
    public static int idMod = 256; // if "hashing" robot id in comms

    public StringBuilder locs;
    public StringBuilder ids;
    public RobotInfo[] infos;
    public int length;
    
    public RobotInfoMap() {
        locs = new StringBuilder();
        ids = new StringBuilder();
        infos = new RobotInfo[32]; //surely we won't have that many bots in vision/comms
        length = 0;
    }
    public RobotInfoMap(int capacity) {
        locs = new StringBuilder();
        ids = new StringBuilder();
        infos = new RobotInfo[capacity];
        length = 0;
    }

    private int getID(RobotInfo i) {
        if (toIdMod) return i.getID() % idMod;
        return i.getID();
    }
    private int getID(int id) {
        if (toIdMod) return id % idMod;
        return id;
    }

    public void set(RobotInfo info) throws Exception {
        int locInd = locs.indexOf(info.getLocation().toString());
        int idInd = ids.indexOf("" + getID(info));
        if (locInd != -1 && idInd != -1 && locInd != idInd) {
            throw new Exception("Wow I guess it happens more than I thought...");
        }
        else if (locInd != -1) {
            infos[locInd / 8] = info;
            ids.replace(locInd, locInd + 8, String.format("%-8s", getID(info)));
        }
        else if (idInd != -1) {
            infos[idInd / 8] = info;
            locs.replace(idInd, idInd + 8, String.format("%-8s", info.getLocation()));
        }
        else {
            infos[length++] = info;
            locs.append(String.format("%-8s", info.getLocation()));
            ids.append(String.format("%-8s", getID(info)));
        }
    }

    public RobotInfo get(MapLocation loc) {
        int locInd = locs.indexOf(loc.toString());
        if (locInd == -1) return null;
        return infos[locInd / 8];
    }

    public RobotInfo get(int id) {
        int idInd = ids.indexOf("" + getID(id));
        if (idInd == -1) return null;
        return infos[idInd / 8];
    }

    public boolean exists(MapLocation loc) {
        return locs.indexOf(loc.toString()) != -1;
    }

    public boolean exists(int id) {
        return ids.indexOf("" + getID(id)) != -1;
    }

    public void clear() {
        length = 0;
        locs = new StringBuilder();
        ids = new StringBuilder();
    }
}
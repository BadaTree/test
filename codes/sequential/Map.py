from math import *

class Map():
    def __init__(self, map_file):
        self.mag = {}
        self.pos = []
        self.mapWidth = 0
        self.mapHeight = 0
        f = open(map_file, "r")
        while True:
            line = f.readline()
            if not line:
                break
            splitData = line.split("\t")
            x = int(splitData[0])
            y = int(splitData[1])
            index = x * 10000 + y
            self.mag[index] = [float(splitData[2]), float(splitData[3]), float(splitData[4])]
            self.pos.append(index)
            if (x > self.mapWidth):
                self.mapWidth = x
            if (y > self.mapHeight):
                self.mapHeight = y
    def getWidth(self):
        return self.mapWidth

    def getHeight(self):
        return self.mapHeight

    def getData(self, dx, dy):
        try:
            return self.mag[10000 * int(round(dx)) + int(round(dy))]
        except:
            return [0.0, 0.0, 0.0]

    def isPossiblePosition(self, dx, dy):
        x = int(round(dx))
        y = int(round(dy))
        if (10000*x + y) not in self.mag.keys():
            return False
        elif (x<0) or (y<0):
            return False
        elif (x>=self.getWidth()) or (y>=self.getHeight()):
            return False
        else:
            return True
        #
        # if (0 <= y <= 101) and (0 <= x <= 764):
        #     return False
        # elif (102 <= y <= 162) and (0 <= x <= 353):
        #     return False
        # elif (163 <= y <= 168) and (0 <= x <= 373):
        #     return False
        # elif (169 <= y <= 198) and (0 <= x <= 377):
        #     return False
        # elif (199 <= y <= 209) and (0 <= x <= 764):
        #     return False
        # elif (210 <= y <= 215) and (0 <= x <= 767):
        #     return False
        # elif (216 <= y <= 221) and (0 <= x <= 773):
        #     return False
        # elif (222 <= y <= 227) and (0 <= x <= 779):
        #     return False
        # elif (228 <= y <= 233) and (0 <= x <= 785):
        #     return False
        # elif (y == 234) and (0 <= x <= 797):
        #     return False
        # elif (16 <= y <= 101) and (778 <= x <= 818):
        #     return False
        # elif (102 <= y <= 125) and (385 <= x <= 764):
        #     return False
        # elif (126 <= y <= 155) and (385 <= x <= 695):
        #     return False
        # elif (156 <= y <= 161) and (391 <= x <= 695):
        #     return False
        # elif (162 <= y <= 179) and (535 <= x <= 695):
        #     return False
        # elif (180 <= y <= 200) and (778 <= x <= 818):
        #     return False
        # elif (16 <= y <= 101) and (832 <= x <= 878):
        #     return False
        # elif (102 <= y <= 118) and (778 <= x <= 818):
        #     return False
        # elif (y == 119) and (778 <= x <= 816):
        #     return False
        # elif (y == 120) and (778 <= x <= 811):
        #     return False
        # elif (y == 121) and (778 <= x <= 808):
        #     return False
        # elif (y == 122) and (778 <= x <= 804):
        #     return False
        # elif (y == 123) and (778 <= x <= 800):
        #     return False
        # elif (y == 124) and (778 <= x <= 796):
        #     return False
        # elif (y == 125) and (778 <= x <= 792):
        #     return False
        # elif (126 <= y <= 137) and (727 <= x <= 764):
        #     return False
        # elif (16 <= y <= 101) and (892 <= x <= 938):
        #     return False
        # elif (102 <= y <= 117) and (832 <= x <= 878):
        #     return False
        # elif (y == 118) and (838 <= x <= 839):
        #     return False
        # elif (y == 119) and (860 <= x <= 878):
        #     return False
        # elif (y == 120) and (868 <= x <= 878):
        #     return False
        # elif (y == 121) and (874 <= x <= 878):
        #     return False
        # elif (122 <= y <= 124) and (892 <= x <= 938):
        #     return False
        # elif (y == 125) and (894 <= x <= 938):
        #     return False
        # elif (y == 126) and (778 <= x <= 787):
        #     return False
        # elif (y == 127) and (778 <= x <= 783):
        #     return False
        # elif (y == 128) and (778 <= x <= 779):
        #     return False
        # elif (y == 129) and (991 <= x <= 938):
        #     return False
        # elif (y == 130) and (916 <= x <= 938):
        #     return False
        # elif (y == 131) and (920 <= x <= 938):
        #     return False
        # elif (y == 132) and (926 <= x <= 938):
        #     return False
        # elif (y == 133) and (931 <= x <= 938):
        #     return False
        # elif (y == 134) and (937 <= x <= 938):
        #     return False
        # elif (102 <= y <= 121) and (892 <= x <= 938):
        #     return False
        # elif (y == 118) and (846 <= x <= 878):
        #     return False
        # elif (y == 126) and (898 <= x <= 938):
        #     return False
        # elif (y == 127) and (902 <= x <= 938):
        #     return False
        # elif (y == 128) and (906 <= x <= 938):
        #     return False
        # else:
        #     return True

    def isPossiblePosition2(self, dx, dy):
        x = int(round(dx))
        y = int(round(dy))
        if (x < 0) or (y < 0):
            return False
        elif (x >= self.getWidth()) or (y >= self.getHeight()):
            return False
        else:
            return True
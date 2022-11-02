from instant.InstantParticle import InstantParticle

class InstantParticle_Mother():
    def __init__(self, my_angle):
        self.particle_children_list = []
        self.my_angle = my_angle
        self.win_num = 0

    def appendChildren(self, position, map_value):
        self.particle_children_list.append(InstantParticle(position, map_value))

    def removeChildren(self, idx):
        del self.particle_children_list[idx]

    def getAvgWeight(self):
        sum = 0
        for c in self.particle_children_list:
            sum += c.weight
        if len(self.particle_children_list) == 0:
            result = 0
        else:
            result = sum / len(self.particle_children_list)
        return result

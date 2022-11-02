class InstantParticle():
    def __init__(self, position, map_value):
        self.x = position[0]
        self.y = position[1]
        self.weight = 0
        self.sequence_average = map_value
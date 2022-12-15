import random
import math
import time
import pandas as pd


#파일 이름 입력
file_name = 'test_magx'

############

file_name += '.txt'

def dir_select(cur_angle, direction_change):
    if direction_change == "normal":
        distr = 12
    elif direction_change == "high":
        distr = 30
    deg = random.gauss(cur_angle, distr)
    rad = math.radians(deg)
    x_return = 6 * math.cos(rad)
    y_return = 6 * math.sin(rad)
    return x_return, y_return, deg


def chk_zero(x, y):
    if map_mag.iloc[math.ceil(x),math.ceil(y)] == 0 or map_mag.iloc[math.ceil(x),math.floor(y)] == 0 or \
            map_mag.iloc[math.floor(x),math.ceil(y)] == 0 or map_mag.iloc[math.floor(x), math.floor(y)] == 0:
        return 0
    return 1


# variable to read txt file
map_mag = pd.read_csv("./data/" + file_name,
    sep = "\t", engine = 'python', encoding = "cp949", header = None)

print("data read success")

# read txt file to array

x_max = map_mag.shape[0] - 1
y_max = map_mag.shape[1] - 1
print(x_max)
print(y_max)



random_path = []
x_default_coordinate = 0
y_default_coordinate = 0
n_step = 100
n_path = 100
x_next_coordinate = 0
y_next_coordinate = 0

cnt = 0
path_save = open("./randompath_data/posco_randompath.txt", 'w')
print("경로 생성 중..")
start = time.time()  # 시작 시간 저장
direction_change = 'normal'
while cnt < n_path:
    x_default_coordinate = random.randint(0, x_max)
    y_default_coordinate = random.randint(0, y_max)
    cur_angle = random.randint(1,360)

    while map_mag.iloc[x_default_coordinate,y_default_coordinate] == 0:
        x_default_coordinate = random.randint(0, x_max)
        y_default_coordinate = random.randint(0, y_max)

    j = 0
    while j < n_step:
        # n_s_step = random.randint(1, 100)
        n_s_step = int(random.gauss(10,15))
        while not (1 <= n_s_step <= 100) :
            n_s_step = int(random.gauss(10, 15))
        if n_s_step > n_step - j:
            n_s_step = n_step - j
        x_additional_coordinate, y_additional_coordinate, cur_angle = dir_select(cur_angle, direction_change)
        k = 0
        while k < n_s_step:
            if (not(0 < x_default_coordinate + x_additional_coordinate < x_max)) or \
                    (not(0 < y_default_coordinate + y_additional_coordinate < y_max)) or \
                    chk_zero(x_default_coordinate + x_additional_coordinate, y_default_coordinate + y_additional_coordinate) == 0:
                x_additional_coordinate, y_additional_coordinate, cur_angle = dir_select(cur_angle, direction_change)
                continue
            x_default_coordinate = x_default_coordinate + x_additional_coordinate
            y_default_coordinate = y_default_coordinate + y_additional_coordinate
            # 생성된 좌표 파일에 출력
            # print(chk_zero(x_default_coordinate, y_default_coordinate))
            path_save.write(str(x_default_coordinate)+"\t"+str(y_default_coordinate)+"\n")
            k += 1
        j += n_s_step
    cnt += 1
    if cnt % 10000 == 0:
        print("10000개 경로 생성 시간 : {}".format(time.time()-start))
        start = time.time()
    if cnt % 4 == 0:
        direction_change = "high"
    else:
        direction_change = "normal"


print("경로 생성 완료!")
# collect remain paths
path_save.close()
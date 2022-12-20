import pandas as pd
import openpyxl
import datetime

import json
import random
import math
import datetime

# wifi_map = list()
# with open(f'./WIFIengine (2)/mag/suwon/mag/suwon_hall.txt', 'r') as f:
#     for line in f.readlines():
#         line = line.replace('\n','').split('\t')
#         wifi_map.append(line)

wifi_data_list = list()
with open(f'./WIFIengine (2)/data/suwon copy/train/train_0/10_time/total.txt', 'r') as f:
    while True:
        line = f.readline()
        if not line: break
        wifi_data = line[:-1].split('\t')
        wifi_data_list.append(wifi_data)

# DATA_SIZE - 걸음 수, DATA_LENGTH - 데이터 개수
DATA_SIZE = 100
DATA_LENGTH = 5

# Field size
MAX_X = len(wifi_data_list)
MAX_Y = len(wifi_data_list[0])

# Initialize Constant
INIT_RAD = (random.random()*1000) % 361
INIT_TIME = datetime.datetime.now()
STEP = 10

rad = INIT_RAD
step = STEP
pre_rad = 0.0

def get_init_area():
    x, y = 0, 0
    # max_x = len(wifi_data_list)
    # max_y = len(wifi_data_list[0])
    # x, y = (random.random()*1000) % max_x, (random.random()*1000) % max_y

    # while is_not_area(x, y):
    #     x, y = (random.random()*1000) % max_x, (random.random()*1000) % max_y
    # return float(round(x)), float(round(y))
    return 1, 1


def is_not_area(x, y):
    for i in range(len(wifi_data_list)):
        if wifi_data_list[i][1] == x and wifi_data_list[i][2] == y: return False
        else: True
    # if wifi_map[int(x)][int(y)]: return False
    # else: return True
    
def get_wifi_data(x, y):
    wifi_data_list = []
    wifi_data = []
    return_data = []
    # with open(f'./WIFIengine (2)/data/suwon copy/train/train_0/10_time/total.txt', 'r') as f:
    #     while True:
    #         line = f.readline()
    #         if not line: break
    #         wifi_data = line[:-1].split('\t')
    #         wifi_data_list.append(wifi_data)

    for line in wifi_data_list:
        if line[1] == str(x) and line[2] == str(y):
            return_data.append([line[4],line[5]])
    return return_data


def get_next_step(x,y,rad):
    global step
    global pre_rad 

    if step:
        while is_not_area(x,y):
            pre_rad = (random.random()*1000) % 361 

            x = x + 1*math.cos(pre_rad)
            y = y + 1*math.sin(pre_rad)

        step = step - 1
        

        # if is_not_area(x + 1*math.cos(rad), y + 1*math.sin(rad)):
        #     pre_rad = 180 - rad
        #     rad = 180 - rad

        # x = x + 1*math.cos(rad)
        # y = y + 1*math.sin(rad)

        # step = step - 1
    else:
        step = 10
        pre_rad = rad
    #     if x > MAX_X:
    #         pre_rad = 180 - rad
    #         rad = 180 - rad
    #         x = x - 1*math.cos(rad)*2
    #     if x < 0:
    #         pre_rad = 180 - rad
    #         rad = 180 - rad
    #         x = x - 1*math.cos(rad)*2

    #     y = y + 1*math.sin(rad)
    #     if y > MAX_Y:
    #         pre_rad = 180 - rad
    #         rad = 180 - rad
    #         y = y - 1*math.sin(rad)*2
    #     if y < 0:
    #         pre_rad = 180 - rad
    #         rad = 180 - rad
    #         y = y - 1*math.sin(rad)*2
    #     step = step - 1
    # else:
    #     step = 10
    #     pre_rad = rad

    return [float(round(x)),float(round(y))]

rad = (random.random()*1000) % 361
now = INIT_TIME
next_step = []
last_step = []

for j in range(DATA_LENGTH):
    f = open(f'./WIFIengine (2)/data/suwon copy/test/test_0/10_time/test_{j}.txt', 'w')
    f.close()

for j in range(DATA_LENGTH):
    INIT_X, INIT_Y = get_init_area()
    INIT_RAD = (random.random()*1000) % 361

    last_step = [INIT_X, INIT_Y, INIT_RAD]


    for i in range(DATA_SIZE):
        next_step = get_next_step(last_step[1], last_step[2], last_step[0])
        wifi_data = get_wifi_data(last_step[1], last_step[2])
        data = ''
        last_step = [
            (random.random()*1000) % 361, 
            float(round(next_step[0])),
            float(round(next_step[1])),
        ]
        for i in range(len(wifi_data)):
            data = 'cnt'+ '\t' + str(last_step[1]) + '\t' + str(last_step[2]) + '\t' + "ADDRESS" + '\t' + str(wifi_data[i][0]) + '\t' + str(wifi_data[i][1]) + '\n'

        f = open(f'./WIFIengine (2)/data/suwon copy/test/test_0/10_time/test_{j}.txt', 'a')
        f.write(data)
        f.close()
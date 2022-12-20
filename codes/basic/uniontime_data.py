import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import copy
import time
import os


# train 1 -> platform 1
# train 2 -> hall
# train 3 -> platform 4
# train 4 -> platform 3
# train 5 -> platform 2

# train 1 -> platform 4
# train 2 -> hall
# train 3 -> platform 1
# train 4 -> platform 2
# train 5 -> platform 3



FILENAME = 'train_0'
MAG = 'suwon_hall'
class wifimodel():
    def __init__(self, location):
        self.train_dir = f'./WIFIengine (2)/data/{location}/train/train_0/10_time/'
        
        self.location = location
        self.model_df = pd.DataFrame()

        self.fil_model_df = pd.DataFrame()

        self.SSID_list = []
        
        self.maxarea = 0
        self.maxarea2 = 0
        self.refwifi = []

        self.rssi_thres = 0
        self.range_num = 0
        self.range_num2 = 0

        file_list = os.listdir(self.train_dir)
        for file in file_list:
            df = pd.read_csv(self.train_dir + file,
                             sep="\t", engine='python', encoding="UTF-8", header=None)
            # idx = df[df[3] == ''].index
            # print(df[df[3].isnull()])
            # df = df.drop(idx)
            df = df[df[3].notnull()]

            df = pd.DataFrame(
                {"x": list(df.iloc[:, 1]), "y": list(df.iloc[:, 2]),
                 "SSID": list(df.iloc[:, -2]),
                 "RSSI": list(df.iloc[:, -1])})
            self.model_df = pd.concat([self.model_df, df])

        self.model_df = self.model_df.drop_duplicates(["x","y","SSID"])

        
        self.mag_df = pd.read_csv(f'./WIFIengine (2)/mag/suwon/mag/{MAG}.txt', sep="\t", engine='python', encoding="cp949", header=None)

        self.ref_mag_df = copy.deepcopy(self.mag_df)
        
        for i in range(self.mag_df.shape[0]):
            for j in range(self.mag_df.shape[1]):
                if ((i % 6 == 0 ) and (j % 6 == 0) and (self.mag_df.iloc[i, j] != 0.0)):
                    self.maxarea += 1
        
        self.maxarea2 = sum(self.ref_mag_df[self.ref_mag_df != 0.0].count())
        self.mag_df[self.mag_df != 0.0] = np.nan

        self.maxX = self.mag_df.shape[0]
        self.maxY = self.mag_df.shape[1]

    def define_range(self, range_num, rssi_range_num):
        self.range_num = range_num
        self.rssi_range_num = rssi_range_num
    
    #RSSI 도 이용하기 대문에 ref_wifi와 rssiwifi를 정의해준다.
    def create_refwifi(self, rssi_thres):
        self.rssi_thres = rssi_thres
        
        fil_model_df = self.model_df.loc[self.model_df['RSSI'] >= self.rssi_thres]

        self.SSID_list = list(fil_model_df['SSID'].unique())
        
        self.refwifi = np.zeros((self.maxX, self.maxY, len(self.SSID_list)), dtype = np.int64)
        self.rssiwifi = np.zeros((self.maxX, self.maxY, len(self.SSID_list)), dtype = np.int64)
        
        self.rssi_max = np.max(fil_model_df['RSSI'])
        for i in range(fil_model_df.shape[0]):
            posx = fil_model_df.iloc[i, 0]
            posy = fil_model_df.iloc[i, 1]
            ssid = fil_model_df.iloc[i, 2]
            rssi = fil_model_df.iloc[i, 3]
            if(0 <= posx < self.maxX) and (0 <= posy < self.maxY):
                self.refwifi[int(posx)][int(posy)][self.SSID_list.index(ssid)] = 1
                #refwifi는 해당 ssid가 존재하면 1로 지정을 해줬는데 rssiwifi는 해당 ssid의 측정된 rssi 값으로 지정해준다.
                self.rssiwifi[int(posx)][int(posy)][self.SSID_list.index(ssid)] = rssi

    def save_refwifi(self):
        if not os.path.isdir(f'./WIFIengine (2)/wifihashmap/{self.location}/'):
            os.makedirs(f'./WIFIengine (2)/wifihashmap/{self.location}/')
        file_name = f'./WIFIengine (2)/wifihashmap/{self.location}/_wifihashmap_{FILENAME}.txt'#{self.location}

        path_save = open(file_name, 'w')

        for x in range(self.maxX + 1):
            for y in range(self.maxY + 1):
                if(x % 6 == 0) and (y % 6 == 0):
                    if(max(self.refwifi[x][y]) != 0):
                        content = str(x) + "\t" + str(y) + "\t" + "\t".join(list(map(str, self.refwifi[x][y]))) + "\n"
                        path_save.write(content)
        path_save.close()
        
        file_name = f'./WIFIengine (2)/wifihashmap/{self.location}/_wifilist_{FILENAME}.txt'#{self.location}

        path_save = open(file_name, 'w')

        content = "\t".join(self.SSID_list)
        path_save.write(content)
        path_save.close()
    
    def save_rssiwifi(self):
        file_name = f'./WIFIengine (2)/wifihashmap/{self.location}/_wifirssihashmap_{FILENAME}.txt' #{self.location}

        path_save = open(file_name, 'w', encoding = 'utf-8-sig')

        for x in range(self.maxX + 1):
            for y in range(self.maxY + 1):
                if(x % 6 == 0) and (y % 6 == 0):
                    if(max(self.refwifi[x][y]) != 0):
                        content = str(x) + "\t" + str(y) + "\t" + "\t".join(list(map(str, self.rssiwifi[x][y]))) + "\n"
                        path_save.write(content)
        path_save.close()

thres_list = [-75]
range_val = [3]#, 6, 7, 8, 9]
rssi_range = [40]
area_thres1 = 0.0
area_thres2 = 1.0

model = wifimodel('suwon copy')


model.create_refwifi(thres_list[0])
model.define_range(range_val[0], rssi_range[0])

model.save_refwifi()
model.save_rssiwifi()


tot_acc_list = []
tot_list_area = []

tot_area_dist = []
for thres in thres_list:
    model.create_refwifi(thres)
    acc_list = []
    list_area = []
    area_dist = []
#     for rang in range_val:
    for range_for_rssi in rssi_range:
        model.define_range(range_val[0], range_for_rssi)
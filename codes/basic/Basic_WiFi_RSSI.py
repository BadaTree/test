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



FILENAME = 'train_2'
TEST = 'test_2'
MAG = 'hall'
class wifimodel():
    def __init__(self, location):
        self.train_dir = f'./WIFIengine (2)/data/{location}/train/{FILENAME}/'
        self.test_dir = f'./WIFIengine (2)/data/{location}/test/{TEST}/'
        
        self.location = location
        self.model_df = pd.DataFrame()
        self.test_df = pd.DataFrame()

        self.fil_model_df = pd.DataFrame()
        self.fil_test_df = pd.DataFrame()

        self.SSID_list = []
        
        self.test_x_list = []
        self.test_y_list = []
        
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
                {"cnt": list(df.iloc[:, 0]), "x": list(df.iloc[:, 1]), "y": list(df.iloc[:, 2]),
                 "ADDR": list(df.iloc[:, 3]),
                 "SSID": list(df.iloc[:, 4]),
                 "RSSI": list(df.iloc[:, 5])})
            self.model_df = pd.concat([self.model_df, df])

        file_list = os.listdir(self.test_dir)
        for file in file_list:
            df = pd.read_csv(self.test_dir + file,
                             sep="\t", engine='python', encoding="UTF-8", header=None)
            df = df[df[3].notnull()]
            df = pd.DataFrame(
                {"cnt": list(df.iloc[:, 0]), "x": list(df.iloc[:, 1]), "y": list(df.iloc[:, 2]),
                 "ADDR": list(df.iloc[:, 3]),
                 "SSID": list(df.iloc[:, 4]),
                 "RSSI": list(df.iloc[:, 5])})
            self.test_df = pd.concat([self.test_df, df])
        
        self.mag_df = pd.read_csv(f'./WIFIengine (2)/mag/{location}/mag/{MAG}.txt', sep="\t", engine='python', encoding="cp949", header=None)

        self.ref_mag_df = copy.deepcopy(self.mag_df)
        
        for i in range(self.mag_df.shape[0]):
            for j in range(self.mag_df.shape[1]):
                if ((i % 6 == 0 ) and (j % 6 == 0) and (self.mag_df.iloc[i, j] != 0.0)):
                    self.maxarea += 1
        
        self.maxarea2 = sum(self.ref_mag_df[self.ref_mag_df != 0.0].count())
        self.mag_df[self.mag_df != 0.0] = np.nan

        self.maxX = self.mag_df.shape[0]
        self.maxY = self.mag_df.shape[1]
        plt.imshow(self.mag_df, cmap='jet', interpolation='none')

    def cal_area(self, posx, posy):
        cnt = 0
        temp = self.ref_mag_df.iloc[int(min(posx)): int(max(posx)), int(min(posy)): int(max(posy))]
        return sum(temp[temp != 0.0].count())
    
    def cal_area2(self, posx, posy):
        cnt = 0
        for i in range(len(posx)):
            if self.mag_df.iloc[posx[i], posy[i]] != 0.0:
                cnt += 1
        return cnt
    
    def define_range(self, range_num, rssi_range_num):
        self.range_num = range_num
        self.rssi_range_num = rssi_range_num
    
    #RSSI 도 이용하기 대문에 ref_wifi와 rssiwifi를 정의해준다.
    def create_refwifi(self, rssi_thres):
        self.rssi_thres = rssi_thres
        
        fil_model_df = self.model_df.loc[self.model_df['RSSI'] >= self.rssi_thres]
        self.fil_test_df = self.test_df.loc[self.test_df['RSSI'] >= self.rssi_thres]

        self.SSID_list = list(fil_model_df['SSID'].unique())
        self.test_x_list = self.fil_test_df['x'].unique()
        self.test_y_list = self.fil_test_df['y'].unique()
        
        self.refwifi = np.zeros((self.maxX, self.maxY, len(self.SSID_list)), dtype = np.int64)
        self.rssiwifi = np.zeros((self.maxX, self.maxY, len(self.SSID_list)), dtype = np.int64)
        
        self.rssi_max = np.max(fil_model_df['RSSI'])
        for i in range(fil_model_df.shape[0]):
            posx = fil_model_df.iloc[i, 1]
            posy = fil_model_df.iloc[i, 2]
            ssid = fil_model_df.iloc[i, 4]
            rssi = fil_model_df.iloc[i, 5]
            if(0 <= posx < self.maxX) and (0 <= posy < self.maxY):
                self.refwifi[int(posx)][int(posy)][self.SSID_list.index(ssid)] = 1
                #refwifi는 해당 ssid가 존재하면 1로 지정을 해줬는데 rssiwifi는 해당 ssid의 측정된 rssi 값으로 지정해준다.
                self.rssiwifi[int(posx)][int(posy)][self.SSID_list.index(ssid)] = rssi

    def test_all(self):
        tot_cnt = 0
        cnt = 0
        self.area_list = []
        coords_list = []
        fail_list = []
        

        for x in self.test_x_list:
            for y in self.test_y_list:
                compare_list = np.array([0 for i in range(len(self.SSID_list))])
                compare_list_rssi = np.array([0 for i in range(len(self.SSID_list))])

                compare_df = self.fil_test_df.loc[(self.fil_test_df["x"] == x) & (self.fil_test_df["y"] == y)]
                if compare_df.empty == False:
                    for i in range(compare_df.shape[0]):
                        if (compare_df.iloc[i, 4] in self.SSID_list):
                            compare_list[self.SSID_list.index(compare_df.iloc[i, 4])] = -1
                            #test data의 rssi 값으로 compare_list_rssi 값을 채워준다.
                            compare_list_rssi[self.SSID_list.index(compare_df.iloc[i, 4])] = compare_df.iloc[i, 5]

                    temp_wifi = self.refwifi - compare_list
                    temp_wifi = np.where(temp_wifi == 1, 0, temp_wifi)
                    
                    #기준 self.rssiwifi와 비교하기 위해 연산을 해주는데
                    #절대값을 해줘서 두 rssi값의 차이를 양수화해준다.
                    temp_rssi_wifi = abs(self.rssiwifi - compare_list_rssi)
                    #한쪽에는 ssid가 확인이 되는데 한쪽에는 확인이 안되게 되면
                    #RSSI 차이 연산시 0 과 측정된 rssi 측정치 (ex. -78) 의 차이를 유사도 비교에 사용하는 것은 무의미하기 때문에
                    #0 과 0이 아닌 값의 차이가 계산된 부분들을 다 0으로 바꿔준다.
                    temp_rssi_wifi = np.where(abs(temp_rssi_wifi) >= abs(self.rssi_max), 0, temp_rssi_wifi)

                    sum_temp = temp_wifi.sum(axis=2)
                    
                    #이제 RSSI 값을 이용하여 유사도 계산을 하게 될텐데
                    #RSSI 값을 비교할때 각 좌표에서의 RSSI 차이 값의 평균을 해주게 된다.
                    #그렇기 때문에 temp_wifi(self.refwifi - compare_list)를 해서 둘다 존재하는 ssid 개수가 0인 좌표에 대해서
                    #평균을 해줄때 분모에 0이 들어가면 안되기 때문에 해당 좌표에 대해서 0인 값을 0에 가까운 값으로 변환해준다.
                    sum_temp_for_rssi = copy.deepcopy(sum_temp)
                    sum_temp_for_rssi = np.where(sum_temp_for_rssi == 0, 0.00001, sum_temp)
                    #temp_rssi_wifi(rssi 차이)를 각 좌표마다 다 더해준다.
                    sum_rssi_temp = temp_rssi_wifi.sum(axis=2)
                    
                    #각 좌표마다 rssi 차이 평균을 계산한다.
                    sum_rssi_temp = sum_rssi_temp / sum_temp_for_rssi
                    
                    #SSID 둘다 존재하는 개수 (sum_temp)의 unique list 확인
                    sum_np = np.unique(sum_temp.flatten())[:: -1]
                    
                    if len(sum_np) <= self.range_num:
                        range_idx = len(sum_np) - 1
                    else:
                        range_idx = self.range_num

                    if range_idx == -1:
                        rangeval = 0
                    else:
                        #self.range_num에 따라 Bit vector 유사도 척도 값 확인
                        rangeval = np.unique(sum_np)[:: - 1][range_idx]
                    
                    #sum_rssi_temp 좌표 중에서 rangeval(Bit vector 유사도 척도) 이상의 좌표들만 이용해서
                    #rssi 값 차이의 평균의 unique list 계산
                    sum_rssi_np = np.unique(sum_rssi_temp[np.where(sum_temp >= rangeval)].flatten())
                    
                    #RSSI vector 유사도 척도 값 계산(Bit vector와 유사)
                    if len(sum_rssi_np) <= self.rssi_range_num:
                        range_idx = len(sum_rssi_np) - 1
                    else:
                        range_idx = self.rssi_range_num

                    if range_idx == -1:
                        rssi_rangeval = 0
                    else:
                        rssi_rangeval = np.unique(sum_rssi_np)[range_idx]
                    
                    #RSSI vector 유사도 척도값 이하(RSSI 값 차이가 작을 수록 유사하기 때문에), Bit vector 유사도 척도 값 이상인
                    #좌표들 확인
                    coords = np.where((sum_temp >= rangeval) & (sum_rssi_temp <= rssi_rangeval))

                    if (min(coords[0]) - 12 <= x <= max(coords[0]) + 12) and (min(coords[1]) - 12 <= y <= max(coords[1]) + 12):
                        cnt += 1
                        coords_list.append([x, y, coords])
                        self.area_list.append((max(coords[0]) - min(coords[0])) * (max(coords[1]) - min(coords[1]))
                                              / (self.maxX * self.maxY))
                    else:
                        fail_list.append([x, y, coords])
                    tot_cnt += 1
                    
                    if (tot_cnt % 100 == 0):
                        print(f"현재 진행 상황 : {tot_cnt} 회")
                        print(f"성공 횟수 : {cnt}회")
        
        
        print(f"test 횟수 : {tot_cnt}, 성공 횟수 : {cnt}")
        print(cnt * 100 / tot_cnt)
        print(np.average(self.area_list))
        
        return coords_list, cnt / tot_cnt, np.average(self.area_list), fail_list
    
    def show_area_dist(self):
        plt.hist(pd.DataFrame(self.area_list).values)
        plt.show()
    
    def test_one(self, idx):
        tot_cnt = 0
        cnt = 0
        self.area_list = []
        coords_list = []
        
        self.fil_test_df['x_y'] = self.fil_test_df['x'].astype('str') + "\t" + self.fil_test_df['y'].astype('str')
        test_list = list(self.fil_test_df['x_y'].unique())[idx].split('\t')
        
        x = float(test_list[0])
        y = float(test_list[1])
        
        compare_list = np.array([0 for i in range(len(self.SSID_list))])
        compare_list_rssi = np.array([0 for i in range(len(self.SSID_list))])
        
        compare_df = self.fil_test_df.loc[(self.fil_test_df["x"] == x) & (self.fil_test_df["y"] == y)]
        
        
        if compare_df.empty == False:
            for i in range(compare_df.shape[0]):
                if (compare_df.iloc[i, 4] in self.SSID_list):
                    compare_list[self.SSID_list.index(compare_df.iloc[i, 4])] = -1
                    compare_list_rssi[self.SSID_list.index(compare_df.iloc[i, 4])] = compare_df.iloc[i, 5]
                    
            temp_wifi = self.refwifi - compare_list
            temp_wifi = np.where(temp_wifi == 1, 0, temp_wifi)
            
            temp_rssi_wifi = abs(self.rssiwifi - compare_list_rssi)
            temp_rssi_wifi = np.where(abs(temp_rssi_wifi) >= abs(self.rssi_max), 0, temp_rssi_wifi)
            
            sum_temp = temp_wifi.sum(axis=2)
            
            sum_temp_for_rssi = copy.deepcopy(sum_temp)
            
            sum_temp_for_rssi = np.where(sum_temp_for_rssi == 0, 0.00001, sum_temp)
            sum_rssi_temp = temp_rssi_wifi.sum(axis=2)
            
            sum_rssi_temp = sum_rssi_temp / sum_temp_for_rssi
            
            sum_np = np.unique(sum_temp.flatten())[:: -1]
            
            if len(sum_np) <= self.range_num:
                range_idx = len(sum_np) - 1
            else:
                range_idx = self.range_num

            if range_idx == -1:
                rangeval = 0
            else:
                rangeval = np.unique(sum_np)[:: - 1][range_idx]
            
            sum_rssi_np = np.unique(sum_rssi_temp[np.where(sum_temp >= rangeval)].flatten())
            
            if len(sum_rssi_np) <= self.rssi_range_num:
                range_idx = len(sum_rssi_np) - 1
            else:
                range_idx = self.rssi_range_num
            
            if range_idx == -1:
                rssi_rangeval = 0
            else:
                rssi_rangeval = np.unique(sum_rssi_np)[range_idx]
                        
            coords = np.where((sum_temp >= rangeval) & (sum_rssi_temp <= rssi_rangeval))
                                                                          
            coords_list.append([x, y, coords])
            self.area_list.append((max(coords[0]) - min(coords[0])) * (max(coords[1]) - min(coords[1]))
                      / (self.maxX * self.maxY))
        
        return coords_list, x, y,coords, sum_np
    
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

test_slicer = 1

model = wifimodel('suwon')


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
        cord, acc, ara, fail = model.test_all()
        acc_list.append(acc)
        list_area.append(ara)
        area_dist.append(model.area_list)
    tot_acc_list.append(acc_list)
    tot_list_area.append(list_area)
    tot_area_dist.append(area_dist)
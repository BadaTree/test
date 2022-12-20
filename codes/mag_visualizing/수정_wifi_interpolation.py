import pandas as pd
import os
import random

directory = './WIFIengine (2)/data/suwon/train/train_5/'
save_file_name = 'total_5.txt'


if __name__ == "__main__":
    file_list = os.listdir(directory)
    save_df = pd.DataFrame([])
    new_df = pd.DataFrame([])
    x_list = []
    y_list = []
    uuid = []
    ssid = []
    rssi = []
    Final_x = []
    Final_y = []
    Final_uuid = []
    Final_ssid = []
    Final_rssi = []
    Floor = 0
    max_x = 0
    max_y = 0

    #층 설정
    Floor = 2

    # 좌표 최댓값 설정
    if Floor == 1 :
        max_x = 180
        max_y = 300
    elif Floor == 2 :
        max_x = 222
        max_y = 666
    elif Floor == 3 :
        max_x = 24
        max_y = 1518
    else :
        max_x = 30
        max_y = 1518

    for file in file_list:
        print(max_x)
        print(max_y)
        if(file != 'total.txt' and file != 'total' and file != '추가할것' and file != '.DS_Store'):
            df = pd.read_csv(directory + file,
            sep = "\t", engine = 'python', encoding = "UTF-8", header = None)
            before_x = df.iloc[0, 1]
            before_y = df.iloc[0, 2]

            # 좌표 하나당 하나 데이터씩만 존재하게 정리
            for i in range(0, df.shape[0]):
                x = df.iloc[i, 1]
                y = df.iloc[i, 2]

                if (df.iloc[i,1] != before_x) | (df.iloc[i,2] != before_y) | (i == 0) :
                    x_list.append(x)
                    y_list.append(y)
                    uuid.append(float(random.random()))
                    ssid.append(float(random.random()))
                    rssi.append(float(random.random()))
                    before_x = df.iloc[i,1]
                    before_y = df.iloc[i,2]
                if i == (df.shape[0]-1 ):
                    new_df = pd.DataFrame({"x_list": x_list, "y_list": y_list,
                                     "uuid": uuid, "ssid": ssid, "rssi": rssi})


            for i in range(0, new_df.shape[0]):
                for j in range(0, 6):
                    if ((y_list[i] + j) <= max_y) & ((x_list[i] + j) <= max_x):
                        y = y_list[i] + j
                        Final_x.append(x_list[i])
                        Final_y.append(y)
                        Final_uuid.append(uuid[i])
                        Final_ssid.append(ssid[i])
                        Final_rssi.append(rssi[i])


            final_df = pd.DataFrame({"Final_x": Final_x, "Final_y": Final_y,
                                   "Final_uuid": Final_uuid, "Final_ssid": Final_ssid, "Final_rssi": Final_rssi})
            # 파일마다의 interpolation 결과 이어 붙이기(합치기)
            save_df = pd.concat([save_df, final_df])
        # 최종 파일 저장
        save_df.to_csv(f'{directory}{save_file_name}', index=False, header=None, sep="\t")



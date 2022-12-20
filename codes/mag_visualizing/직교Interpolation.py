import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
from scipy import interpolate

save_name = 2

if __name__ == "__main__":
    #파일명 / 파일 경로 입력(interpolate할 data들 경로)
    file_name = 'total_5'

    #파일 행 수 체크
    rawdata = pd.read_csv('./WIFIengine (2)/data/suwon/total/' +file_name +'.txt',
    sep = "\t", engine = 'python', encoding = "utf-8", header = None)

    #수집 한 줄 간격
    line_interval = 60.0  # 줄간격 default = 60.0

    # X, Y 최대 좌표 입력(포스코 180, 900) (공학관 78, 408) (하나스퀘어 258, 870) (포스코센터 306, 600)
    maxX = int(np.max(rawdata.iloc[:,0]))
    maxY = int(np.max(rawdata.iloc[:,1]))

    #filter threshold # 기본값 100
    filterth = 100
    fill_value = 10000 # 기본값 10000

    #interpolate type
    inter_type = 'linear'

    #수집한 데이터 잠시 넣어놓을 행렬
    tempmagx = np.empty((maxX+1, maxY+1))
    tempmagy = np.empty((maxX+1, maxY+1))
    tempmagz = np.empty((maxX+1, maxY+1))

    tempmagx.fill(fill_value)
    tempmagy.fill(fill_value)
    tempmagz.fill(fill_value)

    #수집한 데이터 temp행렬에 넣기
    for i in range(rawdata[0].size):
        tempmagx[int(rawdata.iloc[i, 0]), int(rawdata.iloc[i, 1])] = float(rawdata.iloc[i, 2])
        tempmagy[int(rawdata.iloc[i, 0]), int(rawdata.iloc[i, 1])] = float(rawdata.iloc[i, 3])
        tempmagz[int(rawdata.iloc[i, 0]), int(rawdata.iloc[i, 1])] = float(rawdata.iloc[i, 4])

    tempmagx = pd.DataFrame(tempmagx)
    tempmagy = pd.DataFrame(tempmagy)
    tempmagz = pd.DataFrame(tempmagz)


    #interpolate 행렬
    interpolated_byx_array = np.empty((int(maxX/6) + 1, maxY + 1, 5))
    interpolated_byx_array.fill(np.nan)
    interpolated_byy_array = np.empty((int(maxY/6) + 1, maxX + 1, 5))
    interpolated_byy_array.fill(np.nan)

    for idx in range(int(maxX/6) + 1):
        for_x = np.empty(maxY + 1)
        for_x.fill(idx * 6)

        df1 = pd.DataFrame(
            {0: for_x, 1: np.linspace(0, maxY, maxY+1),
             2: tempmagx.iloc[idx*6, :], 3: tempmagy.iloc[idx*6, :], 4: tempmagz.iloc[idx*6, :]})

        interpolated_byx_array[idx, :] = df1

    for idx in range(int(maxY/6) + 1):
        for_y = np.empty(maxX + 1)
        for_y.fill(idx * 6)

        df1 = pd.DataFrame(
            {0: np.linspace(0, maxX, maxX + 1), 1: for_y,
             2: tempmagx.iloc[:, idx*6], 3: tempmagy.iloc[:, idx*6], 4: tempmagz.iloc[:, idx*6]})

        interpolated_byy_array[idx, :] = df1


    #y좌표가 같을 때(수집한 방향이 y축에 평행)
    magx = interpolated_byx_array[:, :, 2]
    magy = interpolated_byx_array[:, :, 3]
    magz = interpolated_byx_array[:, :, 4]

    x = np.linspace(0.0, len(interpolated_byx_array[0, :, 1])-1, len(interpolated_byx_array[0, :, 1]))
    y = np.linspace(0.0, line_interval * (int(maxX/6)), int(maxX/6)+1)

    fmagx = interpolate.interp2d(x, y, magx, kind=inter_type)
    fmagy = interpolate.interp2d(x, y, magy, kind=inter_type)
    fmagz = interpolate.interp2d(x, y, magz, kind=inter_type)
    xnew = x
    ynew = np.linspace(0.0, line_interval * (int(maxX/6)),
                    int((line_interval * (int(maxX/6)))/10)+1)


    x_magx_new = fmagx(xnew, ynew)
    x_magy_new = fmagy(xnew, ynew)
    x_magz_new = fmagz(xnew, ynew)

    x_magx_new[x_magx_new >= filterth] = 0.0
    x_magy_new[x_magy_new >= filterth] = 0.0
    x_magz_new[x_magz_new >= filterth] = 0.0

    total_magx = np.empty((maxX + 1, maxY + 1))
    total_magx.fill(0.0)
    total_magy = np.empty((maxX + 1, maxY + 1))
    total_magy.fill(0.0)
    total_magz = np.empty((maxX + 1, maxY + 1))
    total_magz.fill(0.0)

    total_magx2 = np.empty((maxX + 1, maxY + 1))
    total_magx2.fill(fill_value)
    total_magy2 = np.empty((maxX + 1, maxY + 1))
    total_magy2.fill(fill_value)
    total_magz2 = np.empty((maxX + 1, maxY + 1))
    total_magz2.fill(fill_value)


    #x좌표가 같을 때(수집한 방향이 x축에 평행)
    magx = interpolated_byy_array[:, :, 2]
    magy = interpolated_byy_array[:, :, 3]
    magz = interpolated_byy_array[:, :, 4]

    x = np.linspace(0.0, len(interpolated_byy_array[0, :, 0])-1, len(interpolated_byy_array[0, :, 0]))
    y = np.linspace(0.0, line_interval * (int(maxY/6)), int(maxY/6)+1)

    fmagx = interpolate.interp2d(x, y, magx, inter_type)
    fmagy = interpolate.interp2d(x, y, magy, inter_type)
    fmagz = interpolate.interp2d(x, y, magz, inter_type)
    xnew = x
    ynew = np.linspace(0.0, line_interval * (int(maxY/6)),
                    int((line_interval * (int(maxY/6)))/10)+1)

    y_magx_new = fmagx(xnew, ynew)
    y_magy_new = fmagy(xnew, ynew)
    y_magz_new = fmagz(xnew, ynew)

    y_magx_new = y_magx_new.T
    y_magy_new = y_magy_new.T
    y_magz_new = y_magz_new.T

    y_magx_new[y_magx_new >= filterth] = 0.0
    y_magy_new[y_magy_new >= filterth] = 0.0
    y_magz_new[y_magz_new >= filterth] = 0.0


    for idx8 in range(x_magx_new.shape[0]):
        for idx9 in range(x_magx_new.shape[1]):
            if(x_magx_new [idx8, idx9] != 0.0):
                total_magx[idx8, idx9] = x_magx_new[idx8, idx9]
                total_magx2[idx8, idx9] = x_magx_new[idx8, idx9]

            if (x_magy_new[idx8, idx9] != 0.0):
                total_magy[idx8, idx9] = x_magy_new[idx8, idx9]
                total_magy2[idx8, idx9] = x_magy_new[idx8, idx9]

            if (x_magz_new[idx8, idx9] != 0.0):
                total_magz[idx8, idx9] = x_magz_new[idx8, idx9]
                total_magz2[idx8, idx9] = x_magz_new[idx8, idx9]


    for idx8 in range(x_magx_new.shape[0]):
        for idx9 in range(x_magx_new.shape[1]):
            #magx
            if(total_magx[idx8,idx9]!= 0.0 and y_magx_new[idx8, idx9] != 0.0):
                total_magx[idx8, idx9] += y_magx_new[idx8, idx9]
                total_magx[idx8, idx9] = total_magx[idx8, idx9] / 2
            elif(y_magx_new[idx8, idx9] != 0.0 and total_magx[idx8, idx9] == 0.0):
                total_magx[idx8, idx9] = y_magx_new[idx8, idx9]
            # magy
            if (total_magy[idx8, idx9] != 0.0 and y_magy_new[idx8, idx9] != 0.0):
                total_magy[idx8, idx9] += y_magy_new[idx8, idx9]
                total_magy[idx8, idx9] = total_magy[idx8, idx9] / 2
            elif (y_magy_new[idx8, idx9] != 0.0 and total_magy[idx8, idx9] == 0.0):
                total_magy[idx8, idx9] = y_magy_new[idx8, idx9]
            # magz
            if (total_magz[idx8, idx9] != 0.0 and y_magz_new[idx8, idx9] != 0.0):
                total_magz[idx8, idx9] += y_magz_new[idx8, idx9]
                total_magz[idx8, idx9] = total_magz[idx8, idx9] / 2
            elif (y_magz_new[idx8, idx9] != 0.0 and total_magz[idx8, idx9] == 0.0):
                total_magz[idx8, idx9] = y_magz_new[idx8, idx9]


    for index in range(rawdata.shape[0]):
            total_magx[int(rawdata.iloc[index,0]), int(rawdata.iloc[index,1])] = rawdata.iloc[index,2]
            total_magy[int(rawdata.iloc[index,0]), int(rawdata.iloc[index,1])] = rawdata.iloc[index,3]
            total_magz[int(rawdata.iloc[index,0]), int(rawdata.iloc[index,1])] = rawdata.iloc[index,4]



    # total_magx2[total_magx >= filterth] = 0.0
    # total_magy2[total_magy >= filterth] = 0.0
    # total_magz2[total_magz >= filterth] = 0.0

    total_magx2 = pd.DataFrame(total_magx2)
    total_magy2 = pd.DataFrame(total_magy2)
    total_magz2 = pd.DataFrame(total_magz2)

    for idx in range(int(maxY/6) + 1):
        for_y = np.empty(maxX + 1)
        for_y.fill(idx * 6)

        df1 = pd.DataFrame(
            {0: np.linspace(0, maxX, maxX + 1), 1: for_y,
             2: total_magx2.iloc[:, idx*6], 3: total_magy2.iloc[:, idx*6], 4: total_magz2.iloc[:, idx*6]})

        interpolated_byy_array[idx, :] = df1

    #x좌표가 같을 때(수집한 방향이 x축에 평행)
    magx = interpolated_byy_array[:, :, 2]
    magy = interpolated_byy_array[:, :, 3]
    magz = interpolated_byy_array[:, :, 4]

    x = np.linspace(0.0, len(interpolated_byy_array[0, :, 0])-1, len(interpolated_byy_array[0, :, 0]))
    y = np.linspace(0.0, line_interval * (int(maxY/6)), int(maxY/6)+1)

    fmagx = interpolate.interp2d(x, y, magx, inter_type)
    fmagy = interpolate.interp2d(x, y, magy, inter_type)
    fmagz = interpolate.interp2d(x, y, magz, inter_type)
    xnew = x
    ynew = np.linspace(0.0, line_interval * (int(maxY/6)),
                    int((line_interval * (int(maxY/6)))/10)+1)

    y_magx_new = fmagx(xnew, ynew)
    y_magy_new = fmagy(xnew, ynew)
    y_magz_new = fmagz(xnew, ynew)

    y_magx_new = y_magx_new.T
    y_magy_new = y_magy_new.T
    y_magz_new = y_magz_new.T

    y_magx_new[y_magx_new >= filterth] = 0.0
    y_magy_new[y_magy_new >= filterth] = 0.0
    y_magz_new[y_magz_new >= filterth] = 0.0


    total_magx2[total_magx2 >= filterth] = 0.0
    total_magy2[total_magy2 >= filterth] = 0.0
    total_magz2[total_magz2 >= filterth] = 0.0

    total_magx2 = np.array(total_magx2)
    total_magy2 = np.array(total_magy2)
    total_magz2 = np.array(total_magz2)

    for idx8 in range(x_magx_new.shape[0]):
        for idx9 in range(x_magx_new.shape[1]):
            # magx
            if (y_magx_new[idx8, idx9] != 0.0 and total_magx2[idx8, idx9] == 0.0):
                total_magx2[idx8, idx9] = y_magx_new[idx8, idx9]
            # magy
            if (y_magy_new[idx8, idx9] != 0.0 and total_magy2[idx8, idx9] == 0.0):
                total_magy2[idx8, idx9] = y_magy_new[idx8, idx9]
            # magz
            if (y_magz_new[idx8, idx9] != 0.0 and total_magz2[idx8, idx9] == 0.0):
                total_magz2[idx8, idx9] = y_magz_new[idx8, idx9]

    for idx8 in range(x_magx_new.shape[0]):
        for idx9 in range(x_magx_new.shape[1]):
            # magx
            if (total_magx[idx8, idx9] == 0.0):
                total_magx[idx8, idx9] = total_magx2[idx8, idx9]
            # magy
            if (total_magy[idx8, idx9] == 0.0):
                total_magy[idx8, idx9] = total_magy2[idx8, idx9]
            # magz
            if (total_magz[idx8, idx9] == 0.0):
                total_magz[idx8, idx9] = total_magz2[idx8, idx9]

    total_magx = pd.DataFrame(total_magx)
    total_magy = pd.DataFrame(total_magy)
    total_magz = pd.DataFrame(total_magz)

    #맵저장
    total_magx.to_csv('./WIFIengine (2)/codes/mag_visualizing/onlyinterpol/magx_5.txt', index=False, header=None, sep="\t")
    total_magy.to_csv('./WIFIengine (2)/codes/mag_visualizing/onlyinterpol/magy_5.txt', index=False, header=None, sep="\t")
    total_magz.to_csv('./WIFIengine (2)/codes/mag_visualizing/onlyinterpol/magz_5.txt', index=False, header=None, sep="\t")

    path_save = open('./WIFIengine (2)/codes/mag_visualizing/onlyinterpol/hashmap_5.txt', 'w')

    # for 저장
    for i in range(total_magx[0].size):
        for j in range(total_magx.shape[1]):
            if (total_magx.iloc[i, j] != 0 or total_magy.iloc[i, j] != 0 or total_magz.iloc[i, j] != 0):
                content = str(i) + "\t" + str(j) + "\t" + str(round(total_magx.iloc[i, j], 2)) + "\t" + str(
                    round(total_magy.iloc[i, j], 2)) + "\t" + str(round(total_magz.iloc[i, j], 2)) + "\n"
                path_save.write(content)

    path_save.close()

    print("--------------------------FILE SAVED------------------------------")

    #맵시각화
    total_magx[total_magx == 0.0] = np.nan
    total_magy[total_magy == 0.0] = np.nan
    total_magz[total_magz == 0.0] = np.nan

    total_magx2[total_magx2 == 0.0] = np.nan
    total_magy2[total_magy2 == 0.0] = np.nan
    total_magz2[total_magz2 == 0.0] = np.nan
    figure, axes = plt.subplots()
    axes.set_aspect(1)
    plt.axis('on')
    plt.grid('True')
    plt.imshow(total_magx, cmap='jet', interpolation='none')
    #colorbar range 조절
    # plt.clim(-10,50)
    plt.colorbar()
    plt.show()

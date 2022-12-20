import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import math

if __name__ == "__main__":
    #파일명 입력
    file_name = 'hashmap'

    # 넓혀주기 switch
    extend = True

    ##########

    file_name += '.txt'

    #파일 행 수 체크
    rawdata = pd.read_csv('./onlyinterpol/' +file_name,
    sep = "\t", engine = 'python', encoding = "cp949", header = None)

    #수집 한 줄 간격
    line_interval = 60.0  # 줄간격 default = 60.0

    #X, Y 최대 좌표 입력(포스코 180, 900) (공학관 78, 408)
    maxX = int(np.max(rawdata.iloc[:, 0]))
    maxY = int(np.max(rawdata.iloc[:, 1]))

    #넓혀주기 parameter
    divider = 12
    extendlen = 3



    #수집한 데이터 잠시 넣어놓을 행렬
    tempmagx = np.empty((maxX+1, maxY+1))
    tempmagy = np.empty((maxX+1, maxY+1))
    tempmagz = np.empty((maxX+1, maxY+1))

    tempmagx.fill(0)
    tempmagy.fill(0)
    tempmagz.fill(0)

    #수집한 데이터 temp행렬에 넣기
    for i in range(rawdata[0].size):
        tempmagx[int(rawdata.iloc[i, 0]), int(rawdata.iloc[i, 1])] = rawdata.iloc[i, 2]
        tempmagy[int(rawdata.iloc[i, 0]), int(rawdata.iloc[i, 1])] = rawdata.iloc[i, 3]
        tempmagz[int(rawdata.iloc[i, 0]), int(rawdata.iloc[i, 1])] = rawdata.iloc[i, 4]

    if(extend):
        for i in range(int((tempmagx.shape[0] - 1)/divider) - 2):
            for j in range(tempmagx.shape[1]):
                if(i == 0):
                    #110 인 경우
                    if ((tempmagx[0, j] != 0) and (tempmagx[6, j] != 0) and
                            (tempmagx[12, j] == 0)):
                        for k in range(extendlen):
                            tempmagx[6 + k + 1, j] = tempmagx[6, j]
                            tempmagy[6 + k + 1, j] = tempmagy[6, j]
                            tempmagz[6 + k + 1, j] = tempmagz[6, j]
                    #011인 경우
                    if ((tempmagx[0, j] == 0) and (tempmagx[6, j] != 0) and
                            (tempmagx[12, j] != 0)):
                        for k in range(extendlen):
                            if(6 - extendlen + k >= 0):
                                tempmagx[6 - extendlen + k, j] = tempmagx[divider * i + 6, j]
                                tempmagy[6 - extendlen + k, j] = tempmagy[divider * i + 6, j]
                                tempmagz[6 - extendlen + k, j] = tempmagz[divider * i + 6, j]

                elif(i > 0):
                    #0110인 경우
                    if ((tempmagx[divider * i, j] != 0) and (tempmagx[divider * i + 6, j] != 0) and
                            (tempmagx[divider * i + 12, j] == 0) and (tempmagx[divider * i - 6, j] == 0)):
                        extendlen1 = round(extendlen/2)
                        extendlen2 = extendlen - extendlen1
                        for k in range(extendlen1):
                            if(divider * i + 6 + k + 1 <= int(((tempmagx.shape[0] -  1)/divider))):
                                tempmagx[divider * i + 6 + k + 1, j] = tempmagx[divider * i + 6, j]
                                tempmagy[divider * i + 6 + k + 1, j] = tempmagy[divider * i + 6, j]
                                tempmagz[divider * i + 6 + k + 1, j] = tempmagz[divider * i + 6, j]
                        for k in range(extendlen2):
                            if (divider * i - k - 1 >= 0):
                                tempmagx[divider * i - k - 1, j] = tempmagx[divider * i, j]
                                tempmagy[divider * i - k - 1, j] = tempmagy[divider * i, j]
                                tempmagz[divider * i - k - 1, j] = tempmagz[divider * i, j]

                    if ((tempmagx[divider * i, j] == 0) and (tempmagx[divider * i + 6, j] != 0) and
                            (tempmagx[divider * i + 12, j] != 0)):
                        extendlen1 = round(extendlen / 2)
                        extendlen2 = extendlen - extendlen1

                        for k in range(extendlen1):
                            if(divider * i + 12 + k < tempmagx.shape[0] - 1):
                                tempmagx[divider * i + 12 + k, j] = tempmagx[divider * i + 12, j]
                                tempmagy[divider * i + 12 + k, j] = tempmagy[divider * i + 12, j]
                                tempmagz[divider * i + 12 + k, j] = tempmagz[divider * i + 12, j]

                        for k in range(extendlen2):
                            if(divider * i + 6 - extendlen2 + k >= 0):
                                tempmagx[divider * i + 6 - extendlen2 + k, j] = tempmagx[divider * i + 6, j]
                                tempmagy[divider * i + 6 - extendlen2 + k, j] = tempmagy[divider * i + 6, j]
                                tempmagz[divider * i + 6 - extendlen2 + k, j] = tempmagz[divider * i + 6, j]

        for i in range(tempmagx.shape[0]):
            for j in range(int((tempmagx.shape[1] - 1)/divider) - 2):
                if (j == 0):
                    if ((tempmagx[i, 0] != 0) and (tempmagx[i, 6] != 0) and
                            (tempmagx[i, 12] == 0)):
                        for l in range(extendlen):
                            tempmagx[i, 6 + l + 1] = tempmagx[i, divider * j + 6]
                            tempmagy[i, 6 + l + 1] = tempmagy[i, divider * j + 6]
                            tempmagz[i, 6 + l + 1] = tempmagz[i, divider * j + 6]

                    if ((tempmagx[i, 0] == 0) and (tempmagx[i, 6] != 0) and
                            (tempmagx[i, 12] != 0)): #and (tempmagx[i, divider * j + 18] == 0)):
                        for l in range(extendlen):
                            if(6 - extendlen + l >= 0):
                                tempmagx[i, 6 - extendlen + l] = tempmagx[i, 6]
                                tempmagy[i, 6 - extendlen + l] = tempmagy[i, 6]
                                tempmagz[i, 6 - extendlen + l] = tempmagz[i, 6]

                elif (j > 0):
                    if((tempmagx[i, divider * j] != 0) and (tempmagx[i, divider * j + 6] != 0) and
                            (tempmagx[i , divider * j + 12] == 0) and (tempmagx[i, divider * j - 6] == 0)):
                        extendlen1 = round(extendlen/2)
                        extendlen2 = extendlen - extendlen1

                        for l in range(extendlen1):
                            if (divider * j + 6 + l + 1 <= int(((tempmagx.shape[0] - 1) / divider))):
                                tempmagx[i, divider * j + 6 + l + 1] = tempmagx[i, divider * j + 6]
                                tempmagy[i, divider * j + 6 + l + 1] = tempmagy[i, divider * j + 6]
                                tempmagz[i, divider * j + 6 + l + 1] = tempmagz[i, divider * j + 6]

                        for l in range(extendlen2):
                            if(divider * j - extendlen2 + l >= 0):
                                tempmagx[i, divider * j - extendlen2 + l] = tempmagx[i, divider * j]
                                tempmagy[i, divider * j - extendlen2 + l] = tempmagy[i, divider * j]
                                tempmagz[i, divider * j - extendlen2 + l] = tempmagz[i, divider * j]

                    if ((tempmagx[i, divider * j] == 0) and (tempmagx[i, divider * j + 6] != 0) and
                            (tempmagx[i, divider * j + 12] != 0)):
                        extendlen1 = round(extendlen / 2)
                        extendlen2 = extendlen - extendlen1

                        for l in range(extendlen1):
                            if(i, divider * j + l + 12 + 1 < tempmagx.shape[1]):
                                tempmagx[i, divider * j + l + 12 + 1] = tempmagx[i, divider * j + 12]
                                tempmagy[i, divider * j + l + 12 + 1] = tempmagy[i, divider * j + 12]
                                tempmagz[i, divider * j + l + 12 + 1] = tempmagz[i, divider * j + 12]

                        for l in range(extendlen2):
                            tempmagx[i, divider * j + 6 - extendlen2 + l] = tempmagx[i, divider * j + 6]
                            tempmagy[i, divider * j + 6 - extendlen2 + l] = tempmagy[i, divider * j + 6]
                            tempmagz[i, divider * j + 6 - extendlen2 + l] = tempmagz[i, divider * j + 6]

    for i in range(rawdata[0].size):
        tempmagx[int(rawdata.iloc[i, 0]), int(rawdata.iloc[i, 1])] = rawdata.iloc[i, 2]
        tempmagy[int(rawdata.iloc[i, 0]), int(rawdata.iloc[i, 1])] = rawdata.iloc[i, 3]
        tempmagz[int(rawdata.iloc[i, 0]), int(rawdata.iloc[i, 1])] = rawdata.iloc[i, 4]

    total_magx = pd.DataFrame(tempmagx)
    total_magy = pd.DataFrame(tempmagy)
    total_magz = pd.DataFrame(tempmagz)

    #맵저장
    total_magx.to_csv('./extended/extendmagx.txt', index=False, header=None, sep="\t")
    total_magy.to_csv('./extended/extendmagy.txt', index=False, header=None, sep="\t")
    total_magz.to_csv('./extended/extendmagz.txt', index=False, header=None, sep="\t")

    path_save = open('./extended/extendhashmap.txt', 'w')

    # for 저장
    for i in range(total_magx[0].size):
        for j in range(total_magx.shape[1]):
            if (total_magx.iloc[i, j] != 0 or total_magy.iloc[i, j] != 0 or total_magz.iloc[i, j] != 0):
                content = str(i) + "\t" + str(j) + "\t" + str(round(total_magx.iloc[i, j], 2)) + "\t" + str(
                    round(total_magy.iloc[i, j], 2)) + "\t" + str(round(total_magz.iloc[i, j], 2)) + "\n"
                path_save.write(content)

    path_save.close()

    print('-----------------FILE SAVED----------------------')

    #맵시각화
    total_magx[total_magx==0.0] = np.nan
    total_magy[total_magy==0.0] = np.nan
    total_magz[total_magz==0.0] = np.nan
    figure, axes = plt.subplots()
    axes.set_aspect(1)
    plt.axis('on')
    plt.grid('True')
    plt.imshow(total_magx, cmap='jet', interpolation='none')
    #colorbar range 조절
    # plt.clim(-40,0)
    plt.colorbar()
    plt.show()

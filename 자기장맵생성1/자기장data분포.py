import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns

if __name__ == "__main__":
    #자기장 data 분포 볼 것들 import
    dfx = pd.read_csv('./extended/extendmagx.txt', sep="\t", engine='python', encoding="cp949", header=None)
    dfy = pd.read_csv('./extended/extendmagy.txt', sep="\t", engine='python', encoding="cp949", header=None)
    dfz = pd.read_csv('./extended/extendmagz.txt', sep="\t", engine='python', encoding="cp949", header=None)

    ########원래 Data######
    dfxorigin = dfx
    dfyorigin = dfy
    dfzorigin = dfz

    #outlier 제거 변수(상위 하위 몇프로 ??)
    outlier = 0.15

    #발표용으로 잠깐 쓰는 변수들##################
    outlier_list = [0.1, 0.15, 0.2, 0.25]
    quartile_x_list = []
    quartile_y_list = []
    quartile_z_list = []
    IQRx_list = []
    IQRy_list = []
    IQRz_list = []
    ##########################################

    dftotal = (dfx**2 + dfy**2 + dfz**2) ** (1/2)
    dftotal = dftotal.replace(0, np.NaN)
    dfx = dfx.replace(0, np.NaN)
    dfy = dfy.replace(0, np.NaN)
    dfz = dfz.replace(0, np.NaN)
    dfxorigin = dfxorigin.replace(0, np.NaN)

    df = dftotal.to_numpy().flatten()
    df = pd.DataFrame(df)

    dfxvalue = dfx.to_numpy().flatten()
    dfxvalue = pd.DataFrame(dfxvalue)

    dfyvalue = dfy.to_numpy().flatten()
    dfyvalue = pd.DataFrame(dfyvalue)

    dfzvalue = dfz.to_numpy().flatten()
    dfzvalue = pd.DataFrame(dfzvalue)

    ##########################Outlier 범위 계산##############################################################
    quartile_1 = df.quantile(outlier).values[0]
    quartile_3 = df.quantile(1 - outlier).values[0]
    IQR = quartile_3 - quartile_1

    quartile_x1 = dfxvalue.quantile(outlier).values[0]
    quartile_x3 = dfxvalue.quantile(1 - outlier).values[0]
    IQRx = quartile_x3 - quartile_x1

    quartile_y1 = dfyvalue.quantile(outlier).values[0]
    quartile_y3 = dfyvalue.quantile(1 - outlier).values[0]
    IQRy = quartile_y3 - quartile_y1

    quartile_z1 = dfzvalue.quantile(outlier).values[0]
    quartile_z3 = dfzvalue.quantile(1 - outlier).values[0]
    IQRz = quartile_z3 - quartile_z1
    ########################################################################################################

    #####################################발표할때 쓸 자료 만들기 위해서 잠깐 쓰는거###############################
    # for i in outlier_list:
    #     quartile_x_list.append([dfxvalue.quantile(i).values[0], dfxvalue.quantile(1 - i).values[0]])
    #     IQRx_list.append(dfxvalue.quantile(1-i).values[0] - dfxvalue.quantile(i).values[0])
    #
    #     quartile_y_list.append([dfyvalue.quantile(i).values[0], dfyvalue.quantile(1 - i).values[0]])
    #     IQRy_list.append(dfyvalue.quantile(1-i).values[0] - dfyvalue.quantile(i).values[0])
    #
    #     quartile_z_list.append([dfzvalue.quantile(i).values[0], dfzvalue.quantile(1 - i).values[0]])
    #     IQRz_list.append(dfzvalue.quantile(1-i).values[0] - dfzvalue.quantile(i).values[0])
    #
    # search_df = df[(df >= (quartile_1 - 1.5 * IQR)) & (df <= (quartile_3 + 1.5 * IQR))]
    #
    search_dfx = dfx[(dfx >= (quartile_x1 - 1.5 * IQRx)) & (dfx <= (quartile_x3 + 1.5 * IQRx))]
    search_dfy = dfy[(dfy >= (quartile_y1 - 1.5 * IQRy)) & (dfy <= (quartile_y3 + 1.5 * IQRy))]
    search_dfz = dfz[(dfz >= (quartile_z1 - 1.5 * IQRz)) & (dfz <= (quartile_z3 + 1.5 * IQRz))]
    ########################################################################################################

    ###############################아웃라이어 제거 하는 코드부분#################################################
    print(quartile_x1 - 1.5 * IQRx, quartile_x3 + 1.5 * IQRx, np.mean(search_dfx.mean()))
    print(quartile_y1 - 1.5 * IQRy, quartile_y3 + 1.5 * IQRy, np.mean(search_dfy.mean()))
    print(quartile_y1 - 1.5 * IQRz, quartile_z3 + 1.5 * IQRz, np.mean(search_dfz.mean()))

    dfx[(dfx < (quartile_x1 - 1.5 * IQRx))] = quartile_x1 - 1.5 * IQRx
    dfx[(dfx > (quartile_x3 + 1.5 * IQRx))] = quartile_x3 + 1.5 * IQRx

    dfy[(dfy < (quartile_y1 - 1.5 * IQRz))] = quartile_y1 - 1.5 * IQRy
    dfy[(dfy > (quartile_y3 + 1.5 * IQRz))] = quartile_y3 + 1.5 * IQRy

    dfz[(dfz < (quartile_z1 - 1.5 * IQRz))] = quartile_z1 - 1.5 * IQRz
    dfz[(dfz > (quartile_z3 + 1.5 * IQRz))] = quartile_z3 + 1.5 * IQRz

    dfnewtotal = (dfx ** 2 + dfy ** 2 + dfz ** 2) ** (1 / 2)
    dfnewtotal = dfnewtotal.replace(0, np.NaN)
    #########################################################################################################

    ###################################자기장 분포 보기#########################################################
    magnetx_dist = dfxvalue[(dfxvalue >= (quartile_x1 - 1.5 * IQRx)) & (dfxvalue <= (quartile_x3 + 1.5 * IQRx))]
    magnety_dist = dfyvalue[(dfyvalue >= (quartile_y1 - 1.5 * IQRy)) & (dfyvalue <= (quartile_y3 + 1.5 * IQRy))]
    magnetz_dist = dfzvalue[(dfzvalue >= (quartile_z1 - 1.5 * IQRz)) & (dfzvalue <= (quartile_z3 + 1.5 * IQRz))]
    #
    # # bins = np.linspace(dfxvalue.min() - 10, quartile_x1 - 1.5 * IQRx + 10, 100)
    bins = np.linspace(dfxvalue.min(), dfxvalue.max(), 100)
    #
    # print(dfxvalue.min()[0])
    # out_1 = []
    # out_1.append(quartile_x_list[0][0] - 1.5 * IQRx_list[0])
    # out_1.append(quartile_x_list[0][1] + 1.5 * IQRx_list[0])
    # out_1.append(quartile_x_list[1][0] - 1.5 * IQRx_list[1])
    # out_1.append(quartile_x_list[1][1] + 1.5 * IQRx_list[1])
    # out_1.append(quartile_x_list[2][0] - 1.5 * IQRx_list[2])
    # out_1.append(quartile_x_list[2][1] + 1.5 * IQRx_list[2])
    # out_1.append(quartile_x_list[3][0] - 1.5 * IQRx_list[3])
    # out_1.append(quartile_x_list[3][1] + 1.5 * IQRx_list[3])
    # print(out_1)
    #
    # plt.figure(1)
    # plt.hist(magnetx_dist, bins = bins[:,0])
    # for i in out_1:
    #     plt.axvline(x = i, color = 'r')
    # plt.title('After Outlier Elimination')
    #
    plt.figure(2)
    plt.hist(dfxvalue, bins=bins[:, 0])
    # plt.xlim(quartile_x3 + 1.5 * IQRx, dfxvalue.max()[0])
    # for i in out_1:
    #     plt.axvline(x = i, color = 'r')
    # plt.title('Before Outlier Elimination')
    plt.show()
    ##########################################################################################################

    ############################################for 저장#######################################################
    # dfx = dfx.replace(np.NaN, 0)
    # dfy = dfy.replace(np.NaN, 0)
    # dfz = dfz.replace(np.NaN, 0)
    #
    # dfx.to_csv('./outlierremoved/' + str(outlier) + 'magx.txt', index=False, header=None, sep="\t")
    # dfy.to_csv('./outlierremoved/' + str(outlier) + 'magy.txt', index=False, header=None, sep="\t")
    # dfz.to_csv('./outlierremoved/' + str(outlier) + 'magz.txt', index=False, header=None, sep="\t")
    #
    # file_name = 'hashmap_'+str(outlier) +'.txt'
    # path_save = open('./outlierremoved/' + file_name, 'w')
    #
    # for i in range(dfx[0].size):
    #     for j in range(dfx.shape[1]):
    #         if (dfx.iloc[i, j] != 0 or dfy.iloc[i, j] != 0 or dfz.iloc[i, j] != 0):
    #             content = str(i) + "\t" + str(j) + "\t" + str(round(dfx.iloc[i, j], 2)) + "\t" + str(
    #                 round(dfy.iloc[i, j], 2)) + "\t" + str(round(dfz.iloc[i, j], 2)) + "\n"
    #             path_save.write(content)
    #
    # path_save.close()
    ##################################################################################################

    #######################################시각화######################################################
    # 시각화
    dfx = dfx.replace(0, np.NaN)
    dfy = dfy.replace(0, np.NaN)
    dfz = dfz.replace(0, np.NaN)

    figure, axes = plt.subplots()
    #plt.rcParams["figure.figsize"] = (39, 5.5)
    axes.set_aspect(1)
    plt.axis('off')
    plt.grid('False')
    #plt.xlim(5.5, 24.5)
    #plt.ylim(0, 24.5)
    plt.imshow(dfx, cmap='jet', interpolation='none')
    plt.colorbar()
    #plt.clim(-60,10)
    # plt.scatter(y,x, s=0.5, c='black')
    # plt.scatter(y2,x2, s=0.5, c='red')
    plt.show()
    #################################################################################################
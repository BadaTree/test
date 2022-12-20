import pandas as pd
import os

if __name__ == "__main__":
    floor = 0
    wifi1_list = []
    wifi2_list = []
    wifi3_list = []
    # 3개 버전
    common_list = []
    final_common_list =[]
    common_wifi_num = 0
    final_df = pd.DataFrame([])

    # 비교할 wifilist file 읽어들이기
    df1 = pd.read_csv('./rawdata/1_6_3wifilist.txt', sep="\t", engine='python', encoding="cp949", header=None)
    df2 = pd.read_csv('./rawdata/1_12_3_wifilist.txt', sep="\t", engine='python', encoding="cp949", header=None)
    df3 = pd.read_csv('./rawdata/1_18_3wifilist.txt', sep="\t", engine='python', encoding="cp949", header=None)

    # 비교를 위해 각 wifilist 배열에 담기
    wifi1_list = df1.iloc[0,:]
    wifi2_list = df2.iloc[0,:]
    wifi3_list = df3.iloc[0, :]

    # 먼저 두 개의 list를 비교해 나온 공통 wifi list를 남은 파일 하나와 비교
    # 파일 두 개 비교
    for i in range(0, len(wifi1_list) ):
        for j in  range(0,len(wifi2_list)):
            if wifi1_list[i] == wifi2_list[j]:
                common_list.append(wifi1_list[i])
    # 이전 공통 wifi list와 남은 파일 하나 비교
    for i in range(0, len(common_list) ):
        for j in  range(0,len(wifi3_list)):
            if common_list[i] == wifi3_list[j]:
                common_wifi_num += 1
                final_common_list.append(common_list[i])
    print(len(wifi1_list))
    print(common_wifi_num/len(wifi1_list))
    print(len(wifi2_list))
    print(common_wifi_num / len(wifi2_list))
    print(len(wifi3_list))
    print(common_wifi_num / len(wifi3_list))

    # final_df = pd.DataFrame( {final_common_list})

    # 최종 파일 저장
    # final_df.to_csv('./rawdata/' + floor , index=False, header=None, sep="\t")
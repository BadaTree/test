import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

if __name__ == "__main__":
    #파일 명 입력
    magx_file_name = 'magx.txt'
    magy_file_name = 'magy.txt'
    magz_file_name = 'magz.txt'

    dfx = pd.read_csv(f'./originaldata/{magx_file_name}',
                      sep="\t", engine='python', encoding="cp949", header=None)

    dfy = pd.read_csv(f'./originaldata/{magy_file_name}',
                      sep="\t", engine='python', encoding="cp949", header=None)

    dfz = pd.read_csv(f'./originaldata/{magz_file_name}',
            sep = "\t", engine = 'python', encoding = "cp949", header = None)

    while True:
        xstart = int(input('x값 시작 : '))
        xend = int(input('x값 끝 : '))


        if x < 0 or y < 0 or x >= dfx.shape[0] or y >= dfx.shape[1]:
            break
        print(f'magx : {dfx.iloc[x, y]} magy : {dfy.iloc[x, y]} magz : {dfz.iloc[x, y]}')
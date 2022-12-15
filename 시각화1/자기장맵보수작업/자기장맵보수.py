import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

if __name__ == "__main__":
    #파일 명 입력
    origin_file_name = 'map_z'
    new_file_name = 'magz'

    ############

    origin_file_name += '.txt'
    new_file_name += '.txt'

    dforiginal = pd.read_csv('./originaldata/' + origin_file_name,
            sep = "\t", engine = 'python', encoding = "cp949", header = None)

    df = pd.read_csv('./originaldata/' + origin_file_name,
            sep = "\t", engine = 'python', encoding = "cp949", header = None)

    dfnew = pd.read_csv('./newdata/' + new_file_name,
                             sep="\t", engine='python', encoding="cp949", header=None)

    for i in range(dforiginal.shape[0]):
        for j in range(dforiginal.shape[1]):
            if(dfnew.iloc[i, j] != 0.0):
                dforiginal.iloc[i, j] = dfnew.iloc[i,j]


    dforiginal.to_csv('./output/' + new_file_name, index=False, header=None, sep="\t")

    print('------------FILE SAVED--------------')

    df = pd.concat([df, dforiginal])
    df = df.replace(0, np.NaN)

    #시각화
    figure, axes = plt.subplots()
    axes.set_aspect(1)
    plt.axis('on')
    plt.grid('True')
    plt.imshow(df, cmap='jet', interpolation='none')
    plt.colorbar()
    # plt.clim(-50,50)
    plt.show()
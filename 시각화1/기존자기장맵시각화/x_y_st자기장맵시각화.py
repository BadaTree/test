import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

if __name__ == "__main__":
    #파일 명 입력
    file_name = 'map_x'

    ###########

    file_name += '.txt'

    dforiginal = pd.read_csv('./data/' + file_name,
            sep = "\t", engine = 'python', encoding = "cp949", header = None)

    dforiginal = dforiginal.replace(0, np.NaN)

    #맵 여러개 시각화 할때 사용
    # df = pd.concat([dforiginal, dfoutlier])

    #시각화
    figure, axes = plt.subplots()
    axes.set_aspect(1)
    plt.axis('on')
    plt.grid('True')
    plt.imshow(dforiginal, cmap='jet', interpolation='none')
    # plt.colorbar()
    #plt.clim(-50,50)
    plt.show()
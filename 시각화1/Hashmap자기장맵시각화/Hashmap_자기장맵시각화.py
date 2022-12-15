import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

if __name__ == "__main__":
    #file 이름 입력
    file_name = 'hashmap'

    #############

    file_name += '.txt'

    dftotal = pd.read_csv('./data/' + file_name,
            sep = "\t", engine = 'python', encoding = "cp949", header = None)


    #for 시각화

    interpolated_arrayx = np.empty((np.max(dftotal.iloc[:,0])+1, np.max(dftotal.iloc[:,1])+1))
    interpolated_arrayy = np.empty((np.max(dftotal.iloc[:,0])+1, np.max(dftotal.iloc[:,1])+1))
    interpolated_arrayz = np.empty((np.max(dftotal.iloc[:,0])+1, np.max(dftotal.iloc[:,1])+1))

    interpolated_arrayx.fill(np.nan)
    interpolated_arrayy.fill(np.nan)
    interpolated_arrayz.fill(np.nan)

    for i in range(dftotal[0].size):
        if((dftotal.iloc[i,2] != 0) or (dftotal.iloc[i,3] != 0) or (dftotal.iloc[i,4] != 0) ):
            interpolated_arrayx[dftotal.iloc[i,0], dftotal.iloc[i,1]] = round(dftotal.iloc[i,2],2)
            interpolated_arrayy[dftotal.iloc[i,0], dftotal.iloc[i,1]] = round(dftotal.iloc[i,3],2)
            interpolated_arrayz[dftotal.iloc[i,0], dftotal.iloc[i,1]] = round(dftotal.iloc[i,4],2)

    saveinterpolated_arrayx = pd.DataFrame(interpolated_arrayx)
    saveinterpolated_arrayy = pd.DataFrame(interpolated_arrayy)
    saveinterpolated_arrayz = pd.DataFrame(interpolated_arrayz)

    #시각화
    figure, axes = plt.subplots()
    axes.set_aspect(1)
    plt.axis('on')
    plt.grid('True')
    plt.imshow(interpolated_arrayz, cmap='jet', interpolation='none')
    # plt.colorbar()
    plt.show()
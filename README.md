# wifiData

# 진행 상황 공유
## 수원역 시간대별로 성능 확인 테스트 진행


## 바다_ 진행 사항
### * 진행 중 

### issue
## 민혁_ 현재 진행 중 
### * 진행 중 

## 차후에 진행해야하는 연구
### 1. RF로 층식별
### 2. 핫스팟 필터링
### 3. 실시간 테스트 경량화 



## 1. 모듈별 역할
WIFIengine/codes/ 디렉토리 내부의 모듈별 역할은 다음과 같다.
+ basic
    + Basic_WiFi_RSSI.py  > WIFI 데이터로 시뮬레이션 진행 및 wifi hash map, wifi list 등 생성
    + distribution.py > WIFI 데이터들의 분포를 시각화하고 범위값을 설정해줌
    + findParameters.py > 파라미터 최적화를 진행하여 파라미터별 성능 변화를 시각화 함
+ data_visualize
    + 수정_wifi_interpolation.py > WIFI 데이터들을 하나로 합침
    + 직교Interpolation.py > 합쳐진 WIFI 데이터들을 시각화하여 수집 영역을 result 폴더에 저장함
+ mapAuto
    + gyro2map.py > 자기장 수집 데이터를 기반으로 map을 생성함

***



## 2. 코드 진행 순서
코드 진행 순서는 다음과 같다. 
data_visualize/수정_wifi_interpolation.py -> data_visualize/직교Interpolation.py -> basic/distribution.py -> basic/findParameters.py -> basic/Basic_WiFi_RSSI.py -> mapAuto/gyro2map.py -> 앱 실행

***



## 3. 데이터 디렉토리 구조
데이터들을 여러 파일에서 동시에 사용하기 때문에 디렉토리는 다음의 구조를 지켜서 구성해야함
1. 자기장 데이터 디렉토리
```
WIFIengine
└─────mag
        │──files
        │   ...
        │   ...     
        └            
```
2. Wifi 데이터 디렉토리 
```
WIFIengine
└─────data
       └──{LOCATION}
            │───train
            │    │──part1
            │    │──part2
            │    │  ...
            │    │  ...
            │    └
            └───test
                 │───part1
                 │───part2
                 │  ...                
                 │  ...                
                 └
```

## 4. train data 수정
```
******************* 자기장 *********************** 

# hall
x: 722
y: 770

# platform1 
x : 68
y : 2138

# platform2
x : 86
y : 3920

# platform3 
x : 92
y : 3920

# platform4 
x : 74
y : 2138

******************* 와이파이 *********************** 

# hall
x: 600
y: 432



# platform1 
x : 54
y : 1890

*수정 사항 
1. x36를 x30에 이동
2. x48을 x42에 이동
3. x48를 x54에 이동
4. x54을 x60,66에 복붙

# platform2
x : 60
y : 3516

*수정 사항 
1. x24를 x36에 복붙
2. x36을 x48,60에 복붙
3. x48를 x72에 이동
4. x60을 x84에 이동

# platform3 
x : 60
y : 3516

*수정 사항 
1. x24를 x36에 복붙
2. x42을 x48,60에 이동
3. x48를 x72에 이동
4. x60을 x90에 이동

# platform4 
x : 60
y : 1974

*수정 사항 
1. x54를 x60에 복붙
2. x60을 x72에 이동
```

***
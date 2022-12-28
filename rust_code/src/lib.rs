use std::{collections::HashMap, ops::Index, panic, f32::consts::E};
use bitcoin::consensus::deserialize;
use bitcoin::psbt::Psbt;
use bitcoin::{SchnorrSighashType, Script, Transaction, TxOut};
use bitcoin::blockdata::constants::COIN_VALUE;
use bitcoin::hashes::hex::FromHex;
use bitcoin::psbt::serialize::Deserialize;
use bitcoin::util::sighash;
use bitcoin::util::sighash::SighashCache;
use bitcoin::util::taproot::TapTweakHash;

use bitcoin_serai::crypto::{BitcoinHram, make_even};
use dkg::{ThresholdParams, frost::{KeyGenMachine, SecretShare, SecretShareMachine, Commitments, KeyMachine}, ThresholdKeys};
use rand::rngs::OsRng;
mod java_glue;
pub use crate::java_glue::*;
use modular_frost::{algorithm::{Algorithm, Schnorr}, curve::{Secp256k1, IetfSecp256k1Hram, Ciphersuite}, sign::{AlgorithmMachine, Params, PreprocessMachine, Writable, Preprocess, SignMachine, AlgorithmSignatureMachine, AlgorithmSignMachine, SignatureMachine, self}};
use k256::{elliptic_curve::{sec1::ToEncodedPoint, generic_array::functional::FunctionalSequence}, Scalar, U256};
use k256::elliptic_curve::ops::Reduce;

pub struct SchnorrKeyGenWrapper{
    pub key_params: ThresholdParams,
    pub key_gen_machine: Option<KeyGenMachine<Secp256k1>>,
    pub secret_share_machine: Option<SecretShareMachine<Secp256k1>>,
    pub key_machine: Option<KeyMachine<Secp256k1>>
}

pub struct SchnorrKeyWrapper{
    pub key: ThresholdKeys<Secp256k1>
}

impl SchnorrKeyWrapper {
    fn new () -> SchnorrKeyWrapper{
        panic!();
    }

    fn get_bitcoin_encoded_key(&self) -> Vec<i8>{
        let pubkey_compressed = self.key.group_key().to_encoded_point(true);
        // bitcoin::taproot
        pubkey_compressed.x().to_owned().unwrap()
        .map(|&x|x as i8).to_vec()
    }
    
}

pub struct SignResult1{
    pub wrapper: SchnorrSignWrapper,
    pub preprocess: Vec<u8>
}

impl SignResult1{
    fn new() -> SignResult1{
        panic!()
    }

    fn get_preprocess(&self) -> Vec<i8> {
        self.preprocess.iter().map(|&x| x as i8).collect()
    }
    fn get_wrapper(res: SignResult1) -> SchnorrSignWrapper{
        res.wrapper
    }
}


pub struct SignResult2{
    pub wrapper: SchnorrSignWrapper,
    pub share: Vec<u8>
}

impl SignResult2{
    fn new() -> SignResult2{
        panic!()
    }

    fn get_share(&self) -> Vec<i8>{
        self.share.iter().map(|&x| x as i8).collect()
    }
    fn get_wrapper(res: SignResult2) -> SchnorrSignWrapper{
        res.wrapper
    }
}

pub struct SignParams2{
    pub commitments: HashMap<u16, Vec<u8>>
}

impl SignParams2{
    fn new() -> SignParams2{
        Self{
            commitments: HashMap::default(),
        }
    }

    fn add_commitment_from_user(& mut self, user: u16, commitment: &[i8]){
        let buf_u8 : Vec<u8> = commitment.iter().map(|&x| x as u8).collect();
        
        self.commitments.insert(user, buf_u8);
    }
}

pub struct SignParams3{
    pub shares: HashMap<u16, Vec<u8>>
}

impl SignParams3{
    fn new() -> SignParams3{
        Self{
            shares: HashMap::default(),
        }
    }

    fn add_share_of_user(& mut self, user: u16, share: &[i8]){
        let buf_u8 : Vec<u8> = share.iter().map(|&x| x as u8).collect();
        
        self.shares.insert(user, buf_u8);
    }
}

pub struct SchnorrSignWrapper{
    pub algo_machine: Option<AlgorithmMachine<Secp256k1,Schnorr<Secp256k1,BitcoinHram>>>,
    pub sign_machine: Option<AlgorithmSignMachine<Secp256k1,Schnorr<Secp256k1,BitcoinHram>>>,
    pub signature_machine: Option<AlgorithmSignatureMachine<Secp256k1,Schnorr<Secp256k1,BitcoinHram>>>
}

impl SchnorrSignWrapper{
    fn new() -> SchnorrSignWrapper{
        panic!()
    }

    fn new_instance_for_signing(_key: & SchnorrKeyWrapper, threshold: u32) -> SchnorrSignWrapper{
        let mut key = _key.clone();
        let pubkey_compressed = key.key.group_key().to_encoded_point(true);
        let pubkey =  pubkey_compressed.x().to_owned().unwrap();
        let pubkey_obj =
            secp256k1::XOnlyPublicKey::from_slice(&pubkey_compressed.x().to_owned().unwrap()).unwrap();
        let secp = secp256k1::SECP256K1;
        let tweak = TapTweakHash::from_key_and_tweak(pubkey_obj, None).to_scalar().to_be_bytes();
        key.key.offset(Scalar::from_uint_reduced(U256::from_be_slice(&tweak)));
        Self{
            algo_machine: Some(
                AlgorithmMachine::new(
                Schnorr::<Secp256k1, BitcoinHram>::default(),
                key.key.clone(),
                &(1..threshold+1).into_iter().map(|x| x as u16).collect::<Vec<u16>>()[..]
            ).unwrap()),
            sign_machine: None,
            signature_machine: None
            
        }
    }

    fn sign_1_preprocess(wrapper: SchnorrSignWrapper) -> SignResult1{
        let (sign_machine, preprocess) = wrapper.algo_machine.unwrap().preprocess(& mut OsRng);
        let mut buffer: Vec<u8> = vec![];
        preprocess.write(& mut buffer);

        SignResult1{
            wrapper: Self{
                algo_machine: None,
                sign_machine: Some(sign_machine),
                signature_machine: None
            },
            preprocess: buffer,
        }
    }

    fn sign_2_sign(wrapper: SchnorrSignWrapper, params: SignParams2, msg_i8: &[i8], prev_out_script: &[i8]) -> SignResult2 {
        let msg= unsafe { &*(msg_i8 as *const _  as *const [u8]) };
        let prev_out_script = unsafe { &*(prev_out_script as *const _  as *const [u8]) };
        //assume msg is serialized transaction
        let mut tx = deserialize::<Transaction>(msg).unwrap();
        let psbt = Psbt::from_unsigned_tx(tx).unwrap();
        let hash = SighashCache::new(&psbt.unsigned_tx).taproot_key_spend_signature_hash(
            0,
            &sighash::Prevouts::All(&[TxOut {
                value: COIN_VALUE /100,
                script_pubkey: Script::deserialize(prev_out_script).unwrap(),
            }]),
            SchnorrSighashType::All,
        ).unwrap();
        let sign_machine = wrapper.sign_machine.unwrap();
        let map : HashMap<u16,Preprocess<_,_>> =params.commitments.iter()
        .map(|(&user,buf)|{
            let preprocess = sign_machine.read_preprocess::<&[u8]>(&mut buf.as_ref()).unwrap();
            (user, preprocess)
        }).collect();

        let (signature_machine, sig_share) = sign_machine.sign(map, hash.as_ref()).unwrap();
        let mut buf: Vec<u8> = vec![];
        sig_share.write(& mut buf);
        let buf_i8 : Vec<i8> = buf.iter().map(|&x| x as i8).collect();

        SignResult2{
            wrapper: Self{
                algo_machine: None,
                sign_machine: None,
                signature_machine: Some(signature_machine),
            },
            share: buf,
        }
    }

    fn sign_3_complete(wrapper: SchnorrSignWrapper, params: SignParams3) -> Vec<i8>{
        let signature_machine = wrapper.signature_machine.unwrap();



        let shares_map = params.shares.iter()
        .map(|(&user,buf)|{
            let sig = signature_machine.read_share::<&[u8]>(&mut buf.as_ref()).unwrap();
            (user,sig)
        }).collect();
        let mut _sig = signature_machine.complete(shares_map).unwrap();

        let mut offset = 0;
        (_sig.R, offset) = make_even(_sig.R);
        _sig.s += Scalar::from(offset);

        // mae compatible wth bip340
        let sig = secp256k1::schnorr::Signature::from_slice(&_sig.serialize()[1..65]).unwrap();
        let mut buf = &_sig.serialize()[1..65];
        return buf.iter().map(|&x| x as i8).collect();
    }
}

// pub struct SchnorrWrapper{
//     pub keygen: SchnorrKeyGenWrapper
// }

pub struct ResultKeygen1{
    pub keygen: SchnorrKeyGenWrapper,
    pub res: Vec<i8>
}

impl ResultKeygen1 {
    fn new(keygen: SchnorrKeyGenWrapper, res: &[i8]) -> Self{
        ResultKeygen1 { keygen, res: res.into() }
    }

    fn get_keygen(result: ResultKeygen1) -> SchnorrKeyGenWrapper{
        result.keygen
    }

    fn get_res(&self) -> Vec<i8>{
        self.res.clone()
    }
}

pub struct ParamsKeygen2{
    pub commitment_user_indices: Vec<u16>,
    pub commitments: Vec<Vec<i8>>
}

impl ParamsKeygen2{
    fn new () -> Self{
        ParamsKeygen2 { commitment_user_indices: vec![], commitments: vec![] }
    }

    fn add_commitment_from_user(&mut self,user: u16, commitment: &[i8]){
        self.commitment_user_indices.push(user);
        self.commitments.push(commitment.into());
    }
}

pub struct ParamsKeygen3{
    pub shares_user_indices: Vec<u16>,
    pub shares: Vec<Vec<i8>>
}

impl ParamsKeygen3{
    fn new () -> Self{
        ParamsKeygen3 { shares_user_indices: vec![], shares: vec![] }
    }

    fn add_share_from_user(&mut self,user: u16, shares: &[i8]){
        self.shares_user_indices.push(user);
        self.shares.push(shares.into());
    }
}

pub struct ResultKeygen2{
    pub keygen: SchnorrKeyGenWrapper,
    pub user_indices: Vec<u16>,
    pub shares: Vec<Vec<u8>>
}

impl ResultKeygen2 {
    fn new() -> ResultKeygen2{
        ResultKeygen2{
            keygen: SchnorrKeyGenWrapper::new(1, 1, 1, "".into()),
            user_indices: vec![],
            shares: vec![],
        }
    }
    fn get_keygen(result: ResultKeygen2) -> SchnorrKeyGenWrapper{
        result.keygen
    }
    fn get_user_indices(&self) -> Vec<i32>{
        self.user_indices.iter().map(|&x| x as i32).collect()
    }
    fn get_shares_at(&self, index: usize) -> Vec<i8>{
        self.shares.get(index).unwrap().iter().map(|&x| x as i8).collect()
    }
}

impl SchnorrKeyGenWrapper{
    fn new(threshold: u16, n: u16, index: u16, context: String) -> SchnorrKeyGenWrapper {
        // println!("rip {:?}{:?}{:?}", threshold, n, index);
        let key_params = ThresholdParams::new(threshold,n,index).unwrap();
        SchnorrKeyGenWrapper{
            key_params: key_params,
            key_gen_machine: Some(KeyGenMachine::<Secp256k1>::new(key_params.clone(), context.into())),
            secret_share_machine: None,
            key_machine: None
        }
    }
    // create commitments for the dkg
    fn key_gen_1_create_commitments(wrapper: SchnorrKeyGenWrapper) -> ResultKeygen1{
        let (secret_share_machine, commitments) = wrapper.key_gen_machine.unwrap().generate_coefficients(& mut OsRng);
        let mut buffer: Vec<u8> = vec![];
        commitments.write(&mut buffer);

        let new_wrapper = Self{
            key_params: wrapper.key_params.clone(),
            key_gen_machine: None,
            secret_share_machine: Some(secret_share_machine),
            key_machine: None
        };

        ResultKeygen1{
            keygen: new_wrapper,
            res: buffer.into_iter().map(|x| x as i8).collect()
        }

    }

    //Note, the return is a vec with tuples(for which index the share is for, serialized share)
    fn key_gen_2_generate_shares(wrapper: SchnorrKeyGenWrapper, param_wrapper: ParamsKeygen2) -> ResultKeygen2 {
        let mut commitments_map = HashMap::<u16,Commitments<Secp256k1>>::new();
        let commitments = param_wrapper.commitment_user_indices.iter().zip(param_wrapper.commitments);
        commitments
        .for_each(|(i,bytes_i8)|{
            let bytes: Vec<u8> = bytes_i8.iter().map(|&x| x as u8).collect();
            let commitment = Commitments::<Secp256k1>::read::<&[u8]>(& mut bytes.as_ref(), wrapper.key_params.clone()).unwrap();
            commitments_map.insert(*i, commitment);
        });

        let (key_machine, mut shares) = wrapper.secret_share_machine
            .unwrap().generate_secret_shares(&mut OsRng,commitments_map).unwrap();

        let mut res_wrapper = ResultKeygen2::new();

        shares
        .iter_mut()
        .for_each(|(i,secret_share)|{
            let mut buffer: Vec<u8> = vec![];
            secret_share.write(&mut buffer);
            res_wrapper.user_indices.push(*i);
            res_wrapper.shares.push(buffer);
            // (*i, buffer)
        });

        let new_wrapper = Self{
            key_params: wrapper.key_params.clone(),
            key_gen_machine: None,
            secret_share_machine: None,
            key_machine: Some(key_machine)
        };

        res_wrapper.keygen = new_wrapper;
        res_wrapper
    }

    // shares are the shares the others created for us
    fn key_gen_3_complete(wrapper: SchnorrKeyGenWrapper, param_wrapper: ParamsKeygen3) -> SchnorrKeyWrapper {
        let mut shares_map = HashMap::<u16,SecretShare::<<modular_frost::curve::Secp256k1 as Ciphersuite>::F>>::new();
        let shares = param_wrapper.shares_user_indices.iter().zip(param_wrapper.shares);
        shares
        .for_each(|(from,bytes_i8)|{
            let bytes: Vec<u8> = bytes_i8.iter().map(|&x| x as u8).collect();
            let share = SecretShare::<<modular_frost::curve::Secp256k1 as Ciphersuite>::F>::read::<&[u8]>(&mut bytes.as_ref()).unwrap();
            shares_map.insert(*from, share);
        });

        let core = wrapper.key_machine.unwrap().complete(&mut OsRng, shares_map).unwrap();
        let mut key = ThresholdKeys::new(core);
        let (_,offset) = make_even(key.group_key());
        key = key.offset(Scalar::from(offset));

        SchnorrKeyWrapper{
            key:key
        }
    }
}

// impl SchnorrWrapper{
    // fn new(threshold: u16, n: u16, index: u16, context: String) -> Self{
    //     SchnorrWrapper { keygen: SchnorrKeyGenWrapper::new(threshold, n, index, context) }
    // }

    // fn key_gen_1_create_commitments(mut self) -> Vec<i8>{
    //     let (new_key_gen, ret) = self.keygen.key_gen_1_create_commitments();
    //     self.keygen = new_key_gen;

    //     // compiler should optimize
    //     let vec_i8: Vec<i8> = ret.into_iter().map(|x| x as i8).collect();
    //     vec_i8.into()
    // }
    // fn key_gen_2_generate_shares(mut self, mut commitments: Vec<(u16, Vec<u8>)>) -> Vec<(u16,Vec<u8>)> {
    //     let (new_keygen,res) = self.keygen.key_gen_2_generate_shares(commitments);
    //     self.keygen = new_keygen;
    //     res
    // }
    // fn key_gen_3_complete(mut self, mut shares: Vec<(u16,Vec<u8>)>) {
    //     self.keygen.key_gen_3_complete(shares);
    // }
// }

pub mod test;
